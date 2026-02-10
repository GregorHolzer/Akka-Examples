package actors;

import actors.common.Command;
import actors.common.SharedCommands;
import actors.common.StateMachine;
import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.*;
import akka.actor.typed.pubsub.PubSub;
import akka.actor.typed.pubsub.Topic;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Duration;
import services.DetectorService;

/**
 * Detector actor: captures images, detects persons and communicates with one Surveillance actor.
 * <p>
 * The actor represents a finite state machine with three states:
 * <lu>
 *   <li>{@link DetectorState#Capturing}</li>
 *   <li>{@link DetectorState#Processing}</li>
 *   <li>{@link DetectorState#Alarm}</li>
 * </lu>
 * </p>
 */
public class Detector
  extends AbstractBehavior<Detector.DetectorCommand>
  implements StateMachine<Detector.DetectorState> {

  /** Timeout key to identify scheduled timed messages */
  private static final Object TIMEOUT_KEY = new Object();

  /** Timer that schedules messages to be sent after a specified time */
  private final TimerScheduler<DetectorCommand> timers;

  /** Provides functionality to invoke detector-services */
  private final DetectorService detectorService;

  /** The cameraId that is passed to the Camera-Capture service */
  private final Integer cameraId;

  /** The {@link ActorRef} of the {@link Surveillance} Actor that will receive the {@link Surveillance.FoundPersons} messages */
  private final ActorRef<Surveillance.SurveillanceCommand> surveillanceActorRef;

  /** Timeout to move back to the processing State */
  private final Integer detectorTimeout;

  public Detector(
    ActorContext<DetectorCommand> context,
    TimerScheduler<DetectorCommand> timers,
    DetectorService detectorService,
    Integer cameraId,
    ActorRef<Surveillance.SurveillanceCommand> surveillanceActorRef,
    Integer detectorTimeout
  ) {
    super(context);
    this.timers = timers;
    this.detectorService = detectorService;
    this.cameraId = cameraId;
    this.surveillanceActorRef = surveillanceActorRef;
    this.detectorTimeout = detectorTimeout;
    //Subscribe to the Detector Topic to receive Alarm/Disarm messages
    PubSub pubSub = PubSub.get(getContext().getSystem());
    ActorRef<Topic.Command<DetectorCommand>> detectorTopic = pubSub.topic(
      DetectorCommand.class,
      "global-detector-commands"
    );
    detectorTopic.tell(Topic.subscribe(context.getSelf()));
    //Perform initial service-invocation
    detectorService.cameraCapture(getContext(), cameraId);
  }

  /**
   * Creates a Detector actor.
   *
   * @param cameraId the id of the camera that is passed to the IOT-Service for capturing images.
   * @param surveillanceActorRef the ActorRef of the [Surveillance] Actor to send [Surveillance.FoundPersons] messages to.
   * @param detectorService Provides functionality for service invocations.
   * @return {@link Behavior} of the created {@link Detector} Actor.
   */
  public static Behavior<DetectorCommand> create(
    Integer cameraId,
    ActorRef<Surveillance.SurveillanceCommand> surveillanceActorRef,
    DetectorService detectorService,
    Integer detectorTimeout
  ) {
    return Behaviors.withTimers(timer ->
      Behaviors.setup(context ->
        new Detector(
          context,
          timer,
          detectorService,
          cameraId,
          surveillanceActorRef,
          detectorTimeout
        )
      )
    );
  }

  /**
   * Defines the {@link Behavior} of the  {@link Detector} Actor.
   *
   * @return the created {@link Behavior}.
   */
  @Override
  public Receive<DetectorCommand> createReceive() {
    return newReceiveBuilder()
      .onMessage(CapturedImage.class, this::processing)
      .onMessage(DetectedPersons.class, msg -> Behaviors.same())
      .onMessage(Timeout.class, msg -> Behaviors.same())
      .onMessage(SharedCommands.Alarm.class, this::alarm)
      .onMessage(SharedCommands.Disarm.class, msg -> Behaviors.same())
      .onMessage(
        SharedCommands.InvocationFailure.class,
        this::onInvocationFailure
      )
      .build();
  }

  /**
   * Represents the Capturing-State of the Detector
   * Checks the result of the person detection and may send a message to the {@link Surveillance} Actor to further analyze the image.
   */
  private Behavior<DetectorCommand> capturing() {
    logState(getContext(), DetectorState.Capturing);
    detectorService.cameraCapture(getContext(), cameraId);
    return createReceive();
  }

  /**
   * Represents the Processing-State of the Detector
   *
   * @param capturedImage a message from the {@link DetectorService} that contains the captured image.
   */
  private Behavior<DetectorCommand> processing(CapturedImage capturedImage) {
    logState(getContext(), DetectorState.Processing);
    detectorService.detectPersons(getContext(), capturedImage);
    timers.startSingleTimer(
      TIMEOUT_KEY,
      new Timeout(),
      Duration.ofMillis(detectorTimeout)
    );
    return newReceiveBuilder()
      .onMessage(CapturedImage.class, msg -> Behaviors.same())
      .onMessage(DetectedPersons.class, msg -> {
        if (msg.hasDetectedPersons) {
          surveillanceActorRef.tell(new Surveillance.FoundPersons(msg.image, msg.traceId(), msg.spanId()));
        }
        return Behaviors.same();
      })
      .onMessage(Timeout.class, msg -> this.capturing())
      .onMessage(SharedCommands.Alarm.class, this::alarm)
      .onMessage(SharedCommands.Disarm.class, msg -> Behaviors.same())
      .onMessage(
        SharedCommands.InvocationFailure.class,
        this::onInvocationFailure
      )
      .build();
  }

  /** Represents the Alarm-State of the Detector */
  private Behavior<DetectorCommand> alarm(SharedCommands.Alarm alarm) {
    logState(getContext(), DetectorState.Alarm);
    detectorService.alarmOn(getContext(), alarm);
    return newReceiveBuilder()
      .onMessage(CapturedImage.class, msg -> Behaviors.same())
      .onMessage(DetectedPersons.class, msg -> Behaviors.same())
      .onMessage(SharedCommands.Alarm.class, msg -> Behaviors.same())
      .onMessage(SharedCommands.Disarm.class, msg -> {
        detectorService.alarmOff(getContext());
        return this.capturing();
      })
      .onMessage(Timeout.class, msg -> Behaviors.same())
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
  private Behavior<DetectorCommand> onInvocationFailure(
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

  /** All Detector states. */
  public enum DetectorState {
    Capturing,
    Processing,
    Alarm,
  }

  /** Marker interface for all Detector commands. */
  public interface DetectorCommand extends Command {}

  /**
   * Captured image message.
   *
   * @param image the captured image.
   */
  public record CapturedImage(byte[] image, String traceId, String spanId) implements DetectorCommand {
    @JsonCreator
    public CapturedImage(
            @JsonProperty("image") byte[] image,
            @JsonProperty("traceId") String traceId,
            @JsonProperty("spanId") String spanId) {
      this.image = image;
      this.traceId = traceId;
      this.spanId = spanId;
    }
  }

  /**
   * Result of person detection.
   *
   * @param hasDetectedPersons indicates if the image contains persons
   * @param image the analyzed image
   */
  public record DetectedPersons(
    byte[] image,
    Boolean hasDetectedPersons,
    String traceId,
    String spanId
  ) implements DetectorCommand {
    @JsonCreator
    public DetectedPersons(
      @JsonProperty("image") byte[] image,
      @JsonProperty("hasDetectedPersons") Boolean hasDetectedPersons,
      @JsonProperty("traceId") String traceId,
      @JsonProperty("spanId") String spanId
    ) {
      this.image = image;
      this.hasDetectedPersons = hasDetectedPersons;
      this.traceId = traceId;
      this.spanId = spanId;
    }
  }

  /** Timeout message */
  public static class Timeout implements DetectorCommand {}
}
