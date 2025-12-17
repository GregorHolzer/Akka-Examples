package actors.common;

import actors.DetectorSetup;
import actors.Surveillance;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import services.SurveillanceService;

/**
 * Root guardian actor:
 * <lu>
 *   <li>Loads the configuration.</li>
 *   <li>Creates actors according to the loaded configuration.</li>
 * </lu>
 */
public class Guardian extends AbstractBehavior<Command> {

  private Guardian(ActorContext<Command> context, String configPath) {
    super(context);
    if (
      Configuration.initConfig(context, configPath) == Configuration.ConfigurationStatus.Success
    ) {
      setupComponents();
    }
  }

  /**
   * Creates the Guardian actor.
   *
   * @param configPath path to the JSON config file.
   * @return the Behavior of the created {@link Guardian} Actor.
   */
  public static Behavior<Command> create(String configPath) {
    return Behaviors.setup(context -> new Guardian(context, configPath));
  }

  /**
   * Defines the Behavior of the Guardian Actor that handles no messages.
   */
  @Override
  public Receive<Command> createReceive() {
    return newReceiveBuilder().build();
  }

  /**
   * Spawns all actors based on the loaded configuration.
   */
  private void setupComponents() {
    SurveillanceService surveillanceService = new SurveillanceService();
    Configuration.NodeConfiguration configuration = Configuration.getNodeConfiguration();
    //Create all Surveillance Actors
    configuration
      .surveillanceConfigs()
      .forEach(config ->
        getContext().spawn(
          Surveillance.create(surveillanceService, config.surveillanceId()),
          config.surveillanceId()
        )
      );
    //Create all DetectorSetup Actors
    configuration
      .detectorsConfigs()
      .forEach(config ->
        getContext().spawn(
          DetectorSetup.create(config.detectorId(), config.surveillanceId(), config.cameraId()),
          "Setup_" + config.detectorId()
        )
      );
  }
}
