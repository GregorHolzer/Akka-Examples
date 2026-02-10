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

  /** Timeout to disarm the system */
  private final Integer alarmTimeout;

  private Surveillance(
    ActorContext<Surveillance.SurveillanceCommand> context,
    TimerScheduler<SurveillanceCommand> timers,
    SurveillanceService surveillanceService,
    String surveillanceId,
    Integer alarmTimeout
  ) {
    super(context);
    this.timers = timers;
    this.surveillanceService = surveillanceService;
    this.alarmTimeout = alarmTimeout;
    ServiceKey<SurveillanceCommand> individualSurveillanceKey =
      ServiceKey.create(SurveillanceCommand.class, surveillanceId);
    //Register to be found by DetectorSetup
    getContext()
      .getSystem()
      .receptionist()
      .tell(
        Receptionist.register(individualSurveillanceKey, getContext().getSelf())
      );
    PubSub pubSub = PubSub.get(context.getSystem());
    surveillanceTopic = pubSub.topic(
      SurveillanceCommand.class,
      "global-surveillance-commands"
    );
    surveillanceTopic.tell(Topic.subscribe(getContext().getSelf()));
    detectorTopic = pubSub.topic(
      Detector.DetectorCommand.class,
      "global-detector-commands"
    );
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
    String surveillanceId,
    Integer alarmTimeout
  ) {
    return Behaviors.withTimers(timers ->
      Behaviors.setup(context ->
        new Surveillance(
          context,
          timers,
          surveillanceService,
          surveillanceId,
          alarmTimeout
        )
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
      .onMessage(FoundPersons.class, msg -> {
        surveillanceService.analyze(getContext(), msg);
        return Behaviors.same();
      })
      .onMessage(Analyzed.class, msg -> {
        if (msg.hasThreat) {
          surveillanceTopic.tell(Topic.publish(new SharedCommands.Alarm(msg.traceId, msg.spanId)));
          detectorTopic.tell(Topic.publish(new SharedCommands.Alarm(msg.traceId, msg.spanId)));
          timers.startSingleTimer(
            TIMEOUT_KEY,
            new AlarmTimeout(),
            Duration.ofMillis(alarmTimeout)
          );
          return this.alarm();
        }
        return Behaviors.same();
      })
      .onMessage(SharedCommands.Alarm.class, msg -> this.alarm())
      .onMessage(SharedCommands.Disarm.class, msg -> Behaviors.same())
      .onMessage(AlarmTimeout.class, msg -> Behaviors.same())
      .onMessage(
        SharedCommands.InvocationFailure.class,
        this::onInvocationFailure
      )
      .build();
  }

  /** Represents the Processing-State of the Surveillance Actor */
  private Behavior<SurveillanceCommand> processing() {
    logState(getContext(), SurveillanceState.Processing);
    return createReceive();
  }

  /** Represents the Alarm-State of the Surveillance Actor */
  private Behavior<SurveillanceCommand> alarm() {
    logState(getContext(), SurveillanceState.Alarm);
    return newReceiveBuilder()
      .onMessage(FoundPersons.class, msg -> Behaviors.same())
      .onMessage(Analyzed.class, msg -> Behaviors.same())
      .onMessage(SharedCommands.Alarm.class, msg -> Behaviors.same())
      .onMessage(SharedCommands.Disarm.class, msg -> this.processing())
      .onMessage(AlarmTimeout.class, msg -> {
        detectorTopic.tell(Topic.publish(new SharedCommands.Disarm()));
        surveillanceTopic.tell(Topic.publish(new SharedCommands.Disarm()));
        return processing();
      })
      .onMessage(
        SharedCommands.InvocationFailure.class,
        this::onInvocationFailure
      )
      .build();
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
      .error(
        "Service Invocation of service {} failed",
        invocationFailure.serviceName()
      );
    return Behaviors.same();
  }

  /**
   * All Surveillance states.
   */
  public enum SurveillanceState {
    Processing,
    Alarm,
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
  public record FoundPersons(byte[] image, String traceId, String spanId) implements SurveillanceCommand {
    @JsonCreator
    public FoundPersons(@JsonProperty("image") byte[] image,
                        @JsonProperty("traceId") String traceId,
                        @JsonProperty("spanId") String spanId
    ) {
      this.image = image;
      this.traceId = traceId;
      this.spanId = spanId;
    }
  }

  /**
   * Message that contains the result of an analyzed image.
   *
   * @param image image that has been analyzed
   * @param hasThreat indicates weather the image captured a threat to the system
   */
  public record Analyzed(byte[] image, Boolean hasThreat, String traceId, String spanId) implements
    SurveillanceCommand {
    @JsonCreator
    public Analyzed(@JsonProperty("image") byte[] image, Boolean hasThreat,
                     @JsonProperty("traceId") String traceId,
                    @JsonProperty("spanId") String spanId) {
      this.image = image;
      this.hasThreat = hasThreat;
      this.traceId = traceId;
      this.spanId = spanId;
    }
  }

  /** Message that indicates that the System should be disarmed */
  public static class AlarmTimeout implements SurveillanceCommand {}
}
