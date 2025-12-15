package actors.common;

import actors.setup.BellSetup;
import actors.setup.ControllerSetup;
import actors.setup.GateSetup;
import actors.setup.LightMachineSetup;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;

/**
 * Root guardian actor: loads the configuration and creates actors according to the loaded configuration
 */
public class Guardian extends AbstractBehavior<Command> {

  private Guardian(ActorContext<Command> context, String configPath) {
    super(context);
    if (Configuration.initConfig(getContext(), configPath) == Configuration.ConfigStatus.Success) {
      Telemetry.initOpenTelemetry();
      setupComponent();
    }
  }

  /** Creates the Guardian Actor
   * @param configPath: Path to the ConfigFile
   * */
  public static Behavior<Command> create(String configPath) {
    return Behaviors.setup(context -> new Guardian(context, configPath));
  }

  /** Defines the Behavior of the Guardian that handles no Messages*/
  @Override
  public Receive<Command> createReceive() {
    return newReceiveBuilder().build();
  }

  /** Creates Setup Actors according to the loaded configuration */
  private void setupComponent() {
    //Create Setup Actors for each Component of the Railway-Crossings
    Configuration.NodeConfiguration config = Configuration.getNodeConfiguration();
    config
      .crossings()
      .forEach(crossing ->
        crossing
          .components()
          .forEach(type -> {
            switch (type) {
              case Controller -> {
                getContext().spawn(
                  ControllerSetup.create(crossing.crossingId()),
                  "ControllerSetup" + crossing.crossingId()
                );
                getContext().getLog().info("ControllerSetup has been started successfully");
              }
              case LightMachine -> {
                getContext().spawn(
                  LightMachineSetup.create(crossing.crossingId()),
                  "LightMachineSetup" + crossing.crossingId()
                );
                getContext().getLog().info("LightMachineSetup has been started successfully");
              }
              case Gate -> {
                getContext().spawn(
                  GateSetup.create(crossing.crossingId()),
                  "GateSetup" + crossing.crossingId()
                );
                getContext().getLog().info("GateSetup has been started successfully");
              }
              case Bell -> {
                getContext().spawn(
                  BellSetup.create(crossing.crossingId()),
                  "BellSetup" + crossing.crossingId()
                );
                getContext().getLog().info("BellSetup has been started successfully");
              }
              default -> getContext().getLog().info("No Rule defined for Component_Type {}", type);
            }
          })
      );
  }
}
