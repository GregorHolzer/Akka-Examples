package actors;

import actors.common.Command;
import actors.common.SharedCommands;
import actors.common.StateMachine;
import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.*;
import akka.actor.typed.pubsub.PubSub;
import akka.actor.typed.pubsub.Topic;
import akka.actor.typed.receptionist.Receptionist;
import akka.actor.typed.receptionist.ServiceKey;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Duration;
import services.SurveillanceService;

/**
 * Surveillance actor.
 * <p>
 * Receives detected persons, performs image analysis, sends alarm/disarm messages via PubSub topics.
 * </p>
 * <p>
 * The actor represents a finite state machine with two states:
 * <lu>
 * <li>{@link SurveillanceState#Processing}</li>
 * <li>{@link SurveillanceState#Alarm}</li>
 * </lu>
 * </p>
 */
public class Surveillance
  extends AbstractBehavior<Surveillance.SurveillanceCommand>
  implements StateMachine<Surveillance.SurveillanceState> {

  /**
   * Timeout key to schedule timed messages
   */
  private static final Object TIMEOUT_KEY = new Object();

  /**
   * Timer that schedules messages to be sent after a specified time
   */
  private final TimerScheduler<SurveillanceCommand> timers;

  /**
   * Topic of Surveillance Actors to receive and publish Alarm/Disarm messages
   */
  private final ActorRef<Topic.Command<SurveillanceCommand>> surveillanceTopic;

  /**
   * Topic of Detector Actors to receive Alarm/Disarm messages
   */
  private final ActorRef<Topic.Command<Detector.DetectorCommand>> detectorTopic;

  /**
   * Provides functionality to invoke surveillance-services
   */
  private final SurveillanceService surveillanceService;

  /**
   * Current State of the Surveillance Actor: initial {@link SurveillanceState#Processing}
   */
  private SurveillanceState surveillanceState = SurveillanceState.Processing;

  private Surveillance(
    ActorContext<Surveillance.SurveillanceCommand> context,
    TimerScheduler<SurveillanceCommand> timers,
    SurveillanceService surveillanceService,
    String surveillanceId
  ) {
    super(context);
    this.timers = timers;
    this.surveillanceService = surveillanceService;
    ServiceKey<SurveillanceCommand> individualSurveillanceKey = ServiceKey.create(
      SurveillanceCommand.class,
      surveillanceId
    );
    //Register to be found by DetectorSetup
    getContext()
      .getSystem()
      .receptionist()
      .tell(Receptionist.register(individualSurveillanceKey, getContext().getSelf()));
    PubSub pubSub = PubSub.get(context.getSystem());
    surveillanceTopic = pubSub.topic(SurveillanceCommand.class, "global-surveillance-commands");
    surveillanceTopic.tell(Topic.subscribe(getContext().getSelf()));
    detectorTopic = pubSub.topic(Detector.DetectorCommand.class, "global-detector-commands");
  }

  /**
   * Creates a {@link Surveillance} Actor.
   *
   * @param surveillanceService provides functionality to invoke cloud services
   * @param surveillanceId      the id of the Surveillance Actor
   * @return {@link Behavior} of the created {@link Surveillance} Actor.
   */
  public static Behavior<SurveillanceCommand> create(
    SurveillanceService surveillanceService,
    String surveillanceId
  ) {
    return Behaviors.withTimers(timers ->
      Behaviors.setup(context ->
        new Surveillance(context, timers, surveillanceService, surveillanceId)
      )
    );
  }

  /**
   * Defines the Behavior of the Surveillance Actor.
   *
   * @return the created {@link Behavior}.
   */
  @Override
  public Receive<SurveillanceCommand> createReceive() {
    return newReceiveBuilder()
      .onMessage(FoundPersons.class, this::onFoundPersons)
      .onMessage(Analyzed.class, this::onAnalyzed)
      .onMessage(SharedCommands.Alarm.class, msg -> onAlarm())
      .onMessage(SharedCommands.Disarm.class, msg -> onDisarm())
            .onMessage(SharedCommands.InvocationFailure.class, this::onInvocationFailure)
      .build();
  }

  /**
   * Handles the {@link FoundPersons} message and analyzes the image.
   *
   * @param foundPersons the {@link FoundPersons} message of a {@link Detector} that contains an image.
   */
  private Behavior<SurveillanceCommand> onFoundPersons(FoundPersons foundPersons) {
    if (surveillanceState == SurveillanceState.Processing) {
      surveillanceService.analyze(getContext(), foundPersons);
    }
    return Behaviors.same();
  }

  /**
   * Handles the result of an analyzed image.
   *
   * @param analyzed the {@link Analyzed} message from the {@link SurveillanceService} that contains the result of the analyzed image
   */
  private Behavior<SurveillanceCommand> onAnalyzed(Analyzed analyzed) {
    if (surveillanceState == SurveillanceState.Processing) {
      if (analyzed.hasThreat) {
        //getContext().getSelf().tell(new GlobalCommands.Alarm());
        surveillanceTopic.tell(Topic.publish(new SharedCommands.Alarm()));
        detectorTopic.tell(Topic.publish(new SharedCommands.Alarm()));
      }
    }
    return Behaviors.same();
  }

  /**
   * Handles the {@link SharedCommands.Alarm} message and publishes it to the Surveillance and Detector Topic
   */
  private Behavior<SurveillanceCommand> onAlarm() {
    if (surveillanceState == SurveillanceState.Processing) {
      surveillanceState = SurveillanceState.Alarm;
      logState(getContext(), surveillanceState);
      timers.startSingleTimer(new SharedCommands.Disarm(), Duration.ofMillis(10000));
    }
    return Behaviors.same();
  }

  /**
   * Handles the {@link SharedCommands.Disarm} message and publishes it to the Surveillance and Detector Topic
   */
  private Behavior<SurveillanceCommand> onDisarm() {
    if (surveillanceState == SurveillanceState.Alarm) {
      surveillanceState = SurveillanceState.Processing;
      logState(getContext(), surveillanceState);
      surveillanceTopic.tell(Topic.publish(new SharedCommands.Disarm()));
      detectorTopic.tell(Topic.publish(new SharedCommands.Disarm()));
      timers.cancel(TIMEOUT_KEY);
    }
    return Behaviors.same();
  }

  /**
   * Log the Failure of a Service Invocation.
   *
   * @param invocationFailure the message that contains the name of the failed service invocation
   */
  private Behavior<SurveillanceCommand> onInvocationFailure(
          SharedCommands.InvocationFailure invocationFailure
  ) {
    getContext()
            .getLog()
            .error("Service Invocation of service {} failed", invocationFailure.serviceName());
    return Behaviors.same();
  }

  /**
   * All Surveillance states.
   */
  public enum SurveillanceState {
    Processing,
    Alarm
  }

  /**
   * Marker interface for all surveillance commands.
   */
  public interface SurveillanceCommand extends Command {}

  /**
   * Message that contains an image with a possible threat.
   *
   * @param image image to analyze
   */
  public record FoundPersons(byte[] image) implements SurveillanceCommand {
    @JsonCreator
    public FoundPersons(@JsonProperty("image") byte[] image) {
      this.image = image;
    }
  }

  /**
   * Message that contains the result of an analyzed image.
   *
   * @param image image that has been analyzed
   * @param hasThreat indicates weather the image captured a threat to the system
   */
  public record Analyzed(byte[] image, Boolean hasThreat) implements SurveillanceCommand {
    @JsonCreator
    public Analyzed(@JsonProperty("image") byte[] image, Boolean hasThreat) {
      this.image = image;
      this.hasThreat = hasThreat;
    }
  }
}
