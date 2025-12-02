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
      configuration.surveillanceIds().forEach(surveillanceId -> {
          getContext().spawn(Surveillance.create(surveillanceServices, surveillanceId), surveillanceId);
      });
      configuration.detectors().forEach(detector -> {
          getContext().spawn(DetectorSetup.create(
                  detector.detectorId(),
                  detector.surveillanceId(),
                  detector.cameraId()
                  ), "Setup_" + detector.detectorId());
      });
  }

  @Override
  public Receive<Command> createReceive() {
    return newReceiveBuilder().build();
  }
}
