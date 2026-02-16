package actors.setup;

import actors.Controller;
import actors.Gate;
import actors.LightMachine;

import actors.common.PeripheralMessage;
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
import io.zenoh.Config;
import io.zenoh.Session;
import io.zenoh.Zenoh;
import io.zenoh.keyexpr.KeyExpr;
import io.zenoh.sample.Sample;

import java.util.List;

/**
 * ControllerSetup Actor:
 * <p>
 * <ul>
 * <li> Discovers the {@link LightMachine} and {@link Gate} Actors.</li>
 * <li> Creates the {@link Controller} Actor when the {@link LightMachine} and the {@link Gate} are ready. </li>
 * <li> Receives and forwards messages from Zenoh to the {@link Controller}. </li>
 * </ul>
 * </p>
 */
public class ControllerSetup
        extends AbstractBehavior<Receptionist.Listing>
        implements ComponentSetup {

  /** Attached to the railway-crossing id to identify the component */
  public static final String componentSuffix = "_Controller";

  /** Zenoh key expression that emits sensor events */
  private static final String zenohKeyExpr = "peripheral/sensor";

  /** Logging prefix for the Zenoh Subscriber */
  private static final String zenohLoggingMessage =
          "INFO: Zenoh Subscriber Message -- ";

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

  /** Zenoh Session */
  private Session session = null;

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
            .tell(
                    Receptionist.subscribe(lightMachineServiceKey, getContext().getSelf())
            );
    context
            .getLog()
            .info(
                    "Controller subscribed to ServiceKeys: {}, {}",
                    gateServiceKey,
                    lightMachineServiceKey
            );
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
    return newReceiveBuilder()
            .onMessage(Receptionist.Listing.class, this::onListing)
            .build();
  }

  /**
   * Handles messages of type {@link Receptionist.Listing} from the {@link Receptionist}
   *
   * @param listing message of the {@link Receptionist} that contains a list of {@link ActorRef}s
   */
  private Behavior<Receptionist.Listing> onListing(
          Receptionist.Listing listing
  ) {
    //Check for what ServiceKey the message is
    if (listing.isForKey(gateServiceKey)) {
      List<ActorRef<Gate.GateCommand>> availableGates = listing
              .getServiceInstances(gateServiceKey)
              .stream()
              .toList();
      //Extract Gate ActorRef if available
      gate = checkInstances(
              getContext(),
              availableGates,
              Gate.GateCommand.class
      );
    }
    if (listing.isForKey(lightMachineServiceKey)) {
      List<ActorRef<LightMachine.LightMachineCommand>> availableLightMachines =
              listing.getServiceInstances(lightMachineServiceKey).stream().toList();
      //Extract LightMachine ActorRef if available
      lightMachine = checkInstances(
              getContext(),
              availableLightMachines,
              LightMachine.LightMachineCommand.class
      );
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
    ZenohSetupStatus status = zenohSetup();
    if (status.equals(ZenohSetupStatus.Failure)) {
      getContext().getLog().error("Failed to connect to Zenoh");
    }
  }

  /**
   * Initializes the Zenoh Session and Subscriber
   * @return {@link ZenohSetupStatus#Success} on success, otherwise {@link ZenohSetupStatus#Failure}.
   */
  private ZenohSetupStatus zenohSetup() {
    if (session != null) {
      return ZenohSetupStatus.Success;
    }
    try {
      Config zenohConfig = Config.loadDefault();
      session = Zenoh.open(zenohConfig);
      session.declareSubscriber(KeyExpr.tryFrom(zenohKeyExpr),  this::zenohHandler);
      return ZenohSetupStatus.Success;
    } catch (Exception e) {
      getContext()
              .getLog()
              .error("Could not connect to Zenoh, error: {}", e.getMessage());
      return ZenohSetupStatus.Failure;
    }
  }

  /**
   * Handler that processes arriving messages from Zenoh and forwards them to the {@link Controller}
   * @param sample message data from Zenoh containing a sensor value
   */
  private void zenohHandler(Sample sample) {
    try {
      EventProtos.Event event = EventProtos.Event.parseFrom(sample.getPayload().toBytes());
      //Create a ZenohMessage from the Proto message
      PeripheralMessage message = PeripheralMessage.getNatsMessage(event.getDataList());
      if (message.isValid()) {
        if (message.sensorValue()) {
          //Send a new TrainSeen Message to the Controller
          controller.tell(
                  new Controller.CommandTrainSeen(
                          message.trainSpeed(),
                          message.traceId(),
                          message.spanId()
                  )
          );
        } else {
          //Send a new TrainNotSeen Message to the Controller
          controller.tell(
                  new Controller.CommandTrainNotSeen(
                          message.trainSpeed(),
                          message.traceId(),
                          message.spanId()
                  )
          );
        }
      }
    } catch (InvalidProtocolBufferException e) {
      System.out.println(
              zenohLoggingMessage +
                      "Error parsing Zenoh message to event: " +
                      e.getMessage()
      );
    }
  }

  /** Status of the Zenoh Initialization */
  private enum ZenohSetupStatus {
    Success,
    Failure,
  }
}