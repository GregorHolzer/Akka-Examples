package actors.setup;

import static actors.common.NatsMessage.getNatsMessage;

import actors.common.Configuration;
import actors.Controller;
import actors.Gate;
import actors.LightMachine;
import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import akka.actor.typed.receptionist.Receptionist;
import akka.actor.typed.receptionist.ServiceKey;
import com.google.protobuf.InvalidProtocolBufferException;
import exchange.EventProtos;
import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import io.nats.client.Message;
import io.nats.client.Nats;
import java.util.List;
import actors.common.NatsMessage;

/**
 * ControllerSetup Actor:
 * <p>
 * <ul>
 * <li> Discovers the {@link LightMachine} and {@link Gate} Actors.</li>
 * <li> Creates the {@link Controller} Actor when the {@link LightMachine} and the {@link Gate} are ready. </li>
 * <li> Receives and forwards messages from NATS to the {@link Controller}. </li>
 * </ul>
 * </p>
 */
public class ControllerSetup
  extends AbstractBehavior<Receptionist.Listing>
  implements ComponentSetup {

  /** Attached to the railway-crossing id to identify the component */
  public static final String componentSuffix = "_Controller";

  /** Nats topic that emits sensor events */
  private static final String natsTopic = "peripheral.sensor";

  /** Logging prefix for the NatsDispatcher */
  private static final String natsLoggingMessage = "INFO: Nats Dispatcher Message -- ";

  /** The railway-crossing-id of the Controller */
  public final String crossingId;

  /** The ActorRef of the LightMachine */
  private ActorRef<LightMachine.LightMachineCommand> lightMachine;

  /** The ActorRef of the Gate */
  private ActorRef<Gate.GateCommand> gate;

  /** The ActorRef of the Controller */
  private ActorRef<Controller.ControllerCommand> controller;

  /** The ServiceKey to discover the lightMachine ActorRef from the Receptionist */
  private final ServiceKey<LightMachine.LightMachineCommand> lightMachineServiceKey;

  /** The ServiceKey to discover the gate ActorRef from the Receptionist */
  private final ServiceKey<Gate.GateCommand> gateServiceKey;

  /** Nats Connection */
  private Connection nc = null;

  private ControllerSetup(
    ActorContext<Receptionist.Listing> context,
    String crossingId
  ) {
    super(context);
    this.crossingId = crossingId;
    //Create the ServiceKeys for the Gate and the LightMachine
    gateServiceKey = ServiceKey.create(
      Gate.GateCommand.class,
      crossingId + GateSetup.componentSuffix
    );
    lightMachineServiceKey = ServiceKey.create(
      LightMachine.LightMachineCommand.class,
      crossingId + LightMachineSetup.componentSuffix
    );
    //Subscribe to the Receptionist with the ServiceKeys to discover the Gate and the LightMachine
    getContext()
      .getSystem()
      .receptionist()
      .tell(Receptionist.subscribe(gateServiceKey, getContext().getSelf()));
    getContext()
      .getSystem()
      .receptionist()
      .tell(Receptionist.subscribe(lightMachineServiceKey, getContext().getSelf()));
    context
      .getLog()
      .info("Controller subscribed to ServiceKeys: {}, {}", gateServiceKey, lightMachineServiceKey);
  }

  /**
   * Creates a new {@link ControllerSetup} Actor.
   *
   * @param crossingId  the railway-crossing-id of the {@link Controller} Actor.
   * @return the {@link Behavior} of the created {@link ControllerSetup} Actor.
   */
  public static Behavior<Receptionist.Listing> create(String crossingId) {
    return Behaviors.setup(context -> new ControllerSetup(context, crossingId));
  }

  /** Defines the  {@link Behavior} of the {@link ControllerSetup} Actor that handles messages from the {@link Receptionist}.*/
  @Override
  public Receive<Receptionist.Listing> createReceive() {
    return newReceiveBuilder().onMessage(Receptionist.Listing.class, this::onListing).build();
  }

  /**
   * Handles messages of type {@link Receptionist.Listing} from the {@link Receptionist}
   *
   * @param listing message of the {@link Receptionist} that contains a list of {@link ActorRef}s
   */
  private Behavior<Receptionist.Listing> onListing(Receptionist.Listing listing) {
    //Check for what ServiceKey the message is
    if (listing.isForKey(gateServiceKey)) {
      List<ActorRef<Gate.GateCommand>> availableGates = listing
        .getServiceInstances(gateServiceKey)
        .stream()
        .toList();
      //Extract Gate ActorRef if available
      gate = checkInstances(getContext(), availableGates, Gate.GateCommand.class);
    }
    if (listing.isForKey(lightMachineServiceKey)) {
      List<ActorRef<LightMachine.LightMachineCommand>> availableLightMachines = listing
        .getServiceInstances(lightMachineServiceKey)
        .stream()
        .toList();
      //Extract LightMachine ActorRef if available
      lightMachine = checkInstances(getContext(), availableLightMachines, LightMachine.LightMachineCommand.class);
    }
    //Create the Controller when the LightMachine and Gate are discovered
    if (gate != null && lightMachine != null && controller == null) {
      createController();
    }
    return Behaviors.same();
  }

  /** Creates a new {@link Controller} Actor */
  private void createController() {
    controller = getContext().spawn(
      Controller.create(gate, lightMachine),
      String.format("%s", crossingId + componentSuffix)
    );
    NatsSetupStatus status = natsSetup();
    if (status.equals(NatsSetupStatus.Failure)) {
      getContext().getLog().error("Failed to connect to NATS-Server");
    }
  }

  /**
   * Initializes the Nats-Connection
   * @return {@link NatsSetupStatus#Success} on success, otherwise {@link NatsSetupStatus#Failure}.
   */
  private NatsSetupStatus natsSetup() {
    if (nc != null) {
      return NatsSetupStatus.Success;
    }
    try {
      Configuration.NodeConfiguration config = Configuration.getNodeConfiguration();
      nc = Nats.connect("nats://" + config.nats_server_addr() + ":" + config.nats_server_port());
      Dispatcher dispatcher = nc.createDispatcher(this::NatsDispatcher);
      dispatcher.subscribe(natsTopic);
      getContext().getLog().info("{} subscribed to Topic: {}", crossingId + componentSuffix, natsTopic);
      return NatsSetupStatus.Success;
    } catch (Exception e) {
      getContext().getLog().error("Could not connect to nats server, error: {}", e.getMessage());
      return NatsSetupStatus.Failure;
    }
  }

  /**
   * Dispatcher that handles arriving messages from Nats and forwards them to the {@link Controller}
   * @param msg   a message from Nats containing a sensor value
   */
  private void NatsDispatcher(Message msg) {
    try {
      EventProtos.Event event = EventProtos.Event.parseFrom(msg.getData());
      //Create a NatsMessage from the Proto message
      NatsMessage natsMessage = getNatsMessage(event.getDataList());
      if(natsMessage.isValid()) {
        if (natsMessage.sensorValue()) {
          //Send a new TrainSeen Message to the Controller
          controller.tell(
            new Controller.CommandTrainSeen(
                    natsMessage.trainSpeed(),
                    natsMessage.traceId(),
                    natsMessage.spanId()
            )
          );
        } else {
          //Send a new TrainNotSeen Message to the Controller
          controller.tell(
            new Controller.CommandTrainNotSeen(
                    natsMessage.trainSpeed(),
                    natsMessage.traceId(),
                    natsMessage.spanId()
            )
          );
        }
      }
    } catch (InvalidProtocolBufferException e) {
      System.out.println(
        natsLoggingMessage + "Error parsing nats message to event: " + e.getMessage()
      );
    }
  }

  /** Status of the Nats Initialization */
  private enum NatsSetupStatus {
    Success,
    Failure
  }
}
