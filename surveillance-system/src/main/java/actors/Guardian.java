package actors;

import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import services.SurveillanceServices;

public class Guardian extends AbstractBehavior<Command> {

  private Guardian(ActorContext<Command> context, String configPath) {
    super(context);
    if (
      Configuration.initConfig(context, configPath) == Configuration.ConfigurationStatus.Success
    ) {
      setupComponents();
    }
  }

  public static Behavior<Command> create(String configPath) {
    return Behaviors.setup(context -> new Guardian(context, configPath));
  }

  private void setupComponents() {
      SurveillanceServices surveillanceServices = new SurveillanceServices();
      Configuration.NodeConfiguration configuration = Configuration.getNodeConfiguration();
      configuration.surveillanceConfigs().forEach(config -> getContext().spawn(Surveillance.create(
              surveillanceServices,
              config.groupId(),
              config.surveillanceId()), config.surveillanceId()));
      configuration.detectorsConfigs().forEach(config -> getContext().spawn(DetectorSetup.create(
              config.groupId(),
              config.detectorId(),
              config.surveillanceId(),
              config.cameraId()
              ), "Setup_" + config.detectorId()));
  }

  @Override
  public Receive<Command> createReceive() {
    return newReceiveBuilder().build();
  }
}
