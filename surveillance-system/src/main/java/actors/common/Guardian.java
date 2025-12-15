package actors.common;

import actors.DetectorSetup;
import actors.Surveillance;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import services.SurveillanceService;

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
      SurveillanceService surveillanceService = new SurveillanceService();
      Configuration.NodeConfiguration configuration = Configuration.getNodeConfiguration();
      configuration.surveillanceConfigs().forEach(config -> getContext().spawn(Surveillance.create(
              surveillanceService,
              config.surveillanceId()),
              config.surveillanceId()));
      configuration.detectorsConfigs().forEach(config -> getContext().spawn(DetectorSetup.create(
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
