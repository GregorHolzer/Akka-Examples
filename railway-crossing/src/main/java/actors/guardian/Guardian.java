package actors.guardian;

import actors.Command;
import actors.ComponentType;
import actors.NodeConfig;
import actors.api.SignalReceiver;
import actors.setup.BellSetup;
import actors.setup.ControllerSetup;
import actors.setup.GateSetup;
import actors.setup.LightMachineSetup;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import akka.discovery.Discovery;
import akka.discovery.ServiceDiscovery;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import service.RailwayService;
import service.ServiceLocation;

public class Guardian extends AbstractBehavior<Command> {

  private final RailwayService railwayService;

  private static final String configPath = "/config/config.json";

  private NodeConfig config;

  public static Behavior<Command> create() {
    return Behaviors.setup(Guardian::new);
  }

  public Guardian(ActorContext<Command> context) {
    super(context);
    getNodeConfig();
    ServiceDiscovery discovery = Discovery.get(context.getSystem()).discovery();
    railwayService = new RailwayService(discovery, config);
    railwayService.setupService(context);
    setupComponent();
    context.spawn(SignalReceiver.create(), "SignalReceiver");
  }

  @Override
  public Receive<Command> createReceive() {
    return newReceiveBuilder().build();
  }

  private void getNodeConfig() {
    try {
      ObjectMapper mapper = new ObjectMapper();
      config = mapper.readValue(new File(configPath), NodeConfig.class);
      getContext().getLog().info("Configuration loaded successfully:");
      getContext().getLog().info("Crossing ID: {}", config.crossingId());
      getContext().getLog().info("Component Type: {}", config.componentType());
      getContext().getLog().info("Service Location: {}", config.service_location());
      getContext().getLog().info("Service Name: {}", config.remote_service_name());
    } catch (Exception e) {
      getContext().getLog().error("Error parsing ConfigFile: {}", e.getMessage());
      getContext().getSystem().terminate();
    }
  }

  private void setupComponent() {
    ComponentType componentType = config.componentType();
    switch (componentType) {
      case Controller -> {
        getContext().spawn(ControllerSetup.create(config.crossingId()), "ControllerSetup");
        getContext().getLog().info("ControllerSetup has been started successfully");
      }
      case LightMachine -> {
        getContext().spawn(
          LightMachineSetup.create(config.crossingId(), railwayService),
          "LightMachineSetup"
        );
        getContext().getLog().info("LightMachineSetup has been started successfully");
      }
      case Gate -> {
        getContext().spawn(GateSetup.create(config.crossingId(), railwayService), "GateSetup");
        getContext().getLog().info("GateSetup has been started successfully");
      }
      case Bell -> {
        getContext().spawn(BellSetup.create(config.crossingId(), railwayService), "BellSetup");
        getContext().getLog().info("BellSetup has been started successfully");
      }
      default -> getContext().getLog().info("No Rule defined for Component_Type {}", componentType);
    }
  }
}
