package actors.setup;


import nats.NatsMessage;
import actors.NodeConfig;
import actors.controller.Controller;
import actors.gate.Gate;
import actors.light_machine.LightMachine;
import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import akka.actor.typed.receptionist.Receptionist;
import akka.actor.typed.receptionist.ServiceKey;
import com.google.protobuf.InvalidProtocolBufferException;
import exchange.ContextVariableProtos.*;
import exchange.EventProtos;
import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import io.nats.client.Message;
import io.nats.client.Nats;
import java.util.List;


import static nats.NatsMessage.getNatsMessage;

public class ControllerSetup
  extends AbstractBehavior<Receptionist.Listing>
  implements ComponentSetup {

  private enum NatsSetupStatus {
    Success,
    Failure
  }

  public static final String componentSuffix = "_Controller";

  private static final String natsTopic = "peripheral.sensor";

  private static final String natsLoggingMessage = "INFO: Nats Dispatcher Message -- ";

  public final String crossingId;

  private final String componentName;

  private final NodeConfig config;

  private ActorRef<LightMachine.LightMachineCommand> lightMachine;

  private ActorRef<Gate.GateCommand> gate;

  private ActorRef<Controller.ControllerCommand> controller;

  private final ServiceKey<Gate.GateCommand> gateServiceKey;

  private final ServiceKey<LightMachine.LightMachineCommand> lightMachineServiceKey;

  private Connection nc = null;

  private NatsMessage latestNatsMessage = null;

  private Integer sensorChanges;

  public static Behavior<Receptionist.Listing> create(String crossingId, NodeConfig config) {
    return Behaviors.setup(context -> new ControllerSetup(context, crossingId, config));
  }

  private ControllerSetup(
          ActorContext<Receptionist.Listing> context,
          String crossingId,
          NodeConfig config
  ) {
    super(context);
    this.crossingId = crossingId;
    this.componentName = crossingId + componentSuffix;
    gateServiceKey = ServiceKey.create(
      Gate.GateCommand.class,
      crossingId + GateSetup.componentSuffix
    );
    lightMachineServiceKey = ServiceKey.create(
      LightMachine.LightMachineCommand.class,
      crossingId + LightMachineSetup.componentSuffix
    );
    this.config = config;
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
    sensorChanges = 0;
  }

  @Override
  public Receive<Receptionist.Listing> createReceive() {
    return newReceiveBuilder().onMessage(Receptionist.Listing.class, this::onListing).build();
  }

  private Behavior<Receptionist.Listing> onListing(Receptionist.Listing listing) {
    if (listing.isForKey(gateServiceKey)) {
      List<ActorRef<Gate.GateCommand>> availableGates = listing
        .getServiceInstances(gateServiceKey)
        .stream()
        .toList();
      gate = checkInstances(getContext(), availableGates, Gate.GateCommand.class);
    }
    if (listing.isForKey(lightMachineServiceKey)) {
      List<ActorRef<LightMachine.LightMachineCommand>> availableLightMachines = listing
        .getServiceInstances(lightMachineServiceKey)
        .stream()
        .toList();
      lightMachine = checkInstances(
        getContext(),
        availableLightMachines,
        LightMachine.LightMachineCommand.class
      );
    }
    if (gate != null && lightMachine != null && controller == null) {
      createController();
    }
    return Behaviors.same();
  }

  private void createController() {
    controller = getContext().spawn(
      Controller.create(gate, lightMachine),
      String.format("%s", componentName)
    );
    NatsSetupStatus status = natsSetup();
    while (status.equals(NatsSetupStatus.Failure)) {
      getContext().getLog().info("Retrying connection to nats server...");
      status = natsSetup();
    }
  }

  private NatsSetupStatus natsSetup() {
    if (nc != null) {
      return NatsSetupStatus.Success;
    }
    try {
      nc = Nats.connect("nats://" + config.nats_server_addr() + ":" + config.nats_server_port());
      Dispatcher dispatcher = nc.createDispatcher(this::NatsDispatcher);
      dispatcher.subscribe(natsTopic);
      getContext().getLog().info("{} subscribed to Topic: {}", componentName, natsTopic);
      return NatsSetupStatus.Success;
    } catch (Exception e) {
      getContext().getLog().error("Could not connect to nats server, error: {}", e.getMessage());
      return NatsSetupStatus.Failure;
    }
  }

  private void NatsDispatcher(Message msg){
      try {
          EventProtos.Event event = EventProtos.Event.parseFrom(msg.getData());
          List<ContextVariable> dataList = event.getDataList();
          NatsMessage currentNatsMessage = getNatsMessage(dataList);
          if(latestNatsMessage == null){
              latestNatsMessage = currentNatsMessage;
          }
          if(latestNatsMessage.sensorValue == true && currentNatsMessage.sensorValue == false ){
              sensorChanges++;
              System.out.println(sensorChanges);
          }
          if(currentNatsMessage.sensorValue != latestNatsMessage.sensorValue){
              latestNatsMessage = currentNatsMessage;
          }
          if (!currentNatsMessage.isValid()) {
              System.out.println(
                      natsLoggingMessage +
                              String.format(
                                      "Message was missing values: SensorValue: %s, TrainSpeed: %s, TraceId: %s, SpanId: %s",
                                      currentNatsMessage.sensorValue,
                                      currentNatsMessage.trainSpeed,
                                      currentNatsMessage.traceId,
                                      currentNatsMessage.spanId
                              )
              );
          } else {
                  //System.out.println(natsLoggingMessage + "Sent NatsMessage to controller: " + currentNatsMessage);
                  if (currentNatsMessage.sensorValue) {
                      controller.tell(
                              new Controller.CommandSensorSeen(currentNatsMessage.trainSpeed, currentNatsMessage.traceId, currentNatsMessage.spanId)
                      );
                  } else {
                      controller.tell(
                              new Controller.CommandSensorNotSeen(currentNatsMessage.trainSpeed, currentNatsMessage.traceId, currentNatsMessage.spanId)
                      );
                  }

          }
      } catch (InvalidProtocolBufferException e) {
          System.out.println(
                  natsLoggingMessage + "Error parsing nats message to event: " + e.getMessage()
          );
      }
  }
}
