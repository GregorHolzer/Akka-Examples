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

  /** The current state of the {@link Detector}: initial {@link DetectorState#Capturing} */
  private DetectorState detectorState = DetectorState.Capturing;

  public Detector(
    ActorContext<DetectorCommand> context,
    TimerScheduler<DetectorCommand> timers,
    DetectorService detectorService,
    Integer cameraId,
    ActorRef<Surveillance.SurveillanceCommand> surveillanceActorRef
  ) {
    super(context);
    this.timers = timers;
    this.detectorService = detectorService;
    this.cameraId = cameraId;
    this.surveillanceActorRef = surveillanceActorRef;
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
    DetectorService detectorService
  ) {
    return Behaviors.withTimers(timer ->
      Behaviors.setup(context ->
        new Detector(
          context,
          timer,
          detectorService,
          cameraId,
          surveillanceActorRef
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
      .onMessage(CapturedImage.class, this::onCapturedImage)
      .onMessage(DetectedPersons.class, this::onDetectedPersons)
      .onMessage(Timeout.class, msg -> onTimeout())
      .onMessage(SharedCommands.Alarm.class, msg -> onAlarm())
      .onMessage(SharedCommands.Disarm.class, msg -> onDisarm())
      .onMessage(
        SharedCommands.InvocationFailure.class,
        this::onInvocationFailure
      )
      .build();
  }

  /**
   * Handles the captured image and start person detection.
   *
   * @param capturedImage a message from the {@link DetectorService} that contains the captured image.
   */
  private Behavior<DetectorCommand> onCapturedImage(
    CapturedImage capturedImage
  ) {
    if (detectorState == DetectorState.Capturing) {
      detectorState = DetectorState.Processing;
      logState(getContext(), detectorState);
      detectorService.detectPersons(getContext(), capturedImage);
      timers.startSingleTimer(
        TIMEOUT_KEY,
        new Timeout(),
        Duration.ofMillis(1000)
      );
    }
    return Behaviors.same();
  }

  /**
   * Checks the result of the person detection and may send a message to the {@link Surveillance} Actor to further analyze the image.
   *
   * @param detectedPersons a message from the {@link Surveillance} that contains information about the analyzed image.
   */
  private Behavior<DetectorCommand> onDetectedPersons(
    DetectedPersons detectedPersons
  ) {
    if (detectorState == DetectorState.Processing) {
      if (detectedPersons.hasDetectedPersons) {
        surveillanceActorRef.tell(
          new Surveillance.FoundPersons(detectedPersons.image)
        );
      }
    }
    return Behaviors.same();
  }

  /** Handles the Timeout Message and captures the next image. */
  private Behavior<DetectorCommand> onTimeout() {
    if (detectorState == DetectorState.Processing) {
      detectorState = DetectorState.Capturing;
      logState(getContext(), detectorState);
      detectorService.cameraCapture(getContext(), cameraId);
    }
    return Behaviors.same();
  }

  /** Handles an Alarm Message and turns the alarm on */
  private Behavior<DetectorCommand> onAlarm() {
    //Always move to Alarm-State
    timers.cancel(TIMEOUT_KEY);
    detectorState = DetectorState.Alarm;
    logState(getContext(), detectorState);
    detectorService.alarmOn(getContext());
    return Behaviors.same();
  }

  /** Handles a Disarm Message and turns the alarm off */
  private Behavior<DetectorCommand> onDisarm() {
    if (detectorState == DetectorState.Alarm) {
      detectorState = DetectorState.Capturing;
      logState(getContext(), detectorState);
      detectorService.alarmOff(getContext());
      detectorService.cameraCapture(getContext(), cameraId);
    }
    return Behaviors.same();
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
  public record CapturedImage(byte[] image) implements DetectorCommand {
    @JsonCreator
    public CapturedImage(@JsonProperty("image") byte[] image) {
      this.image = image;
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
    Boolean hasDetectedPersons
  ) implements DetectorCommand {
    @JsonCreator
    public DetectedPersons(
      @JsonProperty("image") byte[] image,
      @JsonProperty("hasDetectedPersons") Boolean hasDetectedPersons
    ) {
      this.image = image;
      this.hasDetectedPersons = hasDetectedPersons;
    }
  }

  /** Timeout message */
  public static class Timeout implements DetectorCommand {}
}
