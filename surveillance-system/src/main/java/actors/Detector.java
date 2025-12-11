package actors;

import actors.common.Command;
import actors.common.GlobalCommands;
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

public class Detector extends AbstractBehavior<Detector.DetectorCommand> implements StateMachine<Detector.DetectorState> {

  private static final Object TIMEOUT_KEY = new Object();

  private final TimerScheduler<DetectorCommand> timers;

  private final DetectorService detectorService;

  private final Integer cameraId;

  private final ActorRef<Surveillance.SurveillanceCommand> surveillanceActorRef;

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
    PubSub pubSub = PubSub.get(getContext().getSystem());
    ActorRef<Topic.Command<DetectorCommand>> detectorTopic = pubSub.topic(DetectorCommand.class, "global-detector-commands");
    detectorTopic.tell(Topic.subscribe(context.getSelf()));
    detectorService.cameraCapture(getContext(), cameraId);
  }

  public static Behavior<DetectorCommand> create(
          Integer cameraId,
    ActorRef<Surveillance.SurveillanceCommand> surveillanceActorRef,
    DetectorService detectorService
  ) {
    return Behaviors.withTimers(timer ->
      Behaviors.setup(context ->
        new Detector(context, timer, detectorService, cameraId, surveillanceActorRef)
      )
    );
  }

  @Override
  public Receive<DetectorCommand> createReceive() {
    return newReceiveBuilder()
            .onMessage(CapturedImage.class, this::onCapturedImage)
            .onMessage(DetectedPersons.class, this::onDetectedPersons)
            .onMessage(Timeout.class, msg -> onTimeout())
            .onMessage(GlobalCommands.Alarm.class, msg -> onAlarm())
            .onMessage(GlobalCommands.Disarm.class, msg -> onDisarm())
            .onMessage(GlobalCommands.InvocationFailure.class, this::onInvocationFailure)
            .build();
  }

  private Behavior<DetectorCommand> onCapturedImage(CapturedImage capturedImage) {
    if (detectorState == DetectorState.Capturing) {
      detectorState = DetectorState.Processing;
        logState(getContext(), detectorState);
      detectorService.detectPersons(getContext(), capturedImage);
      timers.startSingleTimer(TIMEOUT_KEY, new Timeout(), Duration.ofMillis(500));
    }
    return Behaviors.same();
  }

  private Behavior<DetectorCommand> onDetectedPersons(DetectedPersons detectedPersons) {
    if (detectorState == DetectorState.Processing) {
      if (detectedPersons.hasDetectedPersons) {
        surveillanceActorRef.tell(new Surveillance.FoundPersons(detectedPersons.image));
      }
    }
    return Behaviors.same();
  }

  private Behavior<DetectorCommand> onTimeout() {
    if (detectorState == DetectorState.Processing) {
      detectorState = DetectorState.Capturing;
        logState(getContext(), detectorState);
      detectorService.cameraCapture(getContext(), cameraId);
    }
    return Behaviors.same();
  }

  private Behavior<DetectorCommand> onAlarm() {
    if (detectorState == DetectorState.Processing) {
      timers.cancel(TIMEOUT_KEY);
      detectorState = DetectorState.Alarm;
        logState(getContext(), detectorState);
      detectorService.alarmOn(getContext());
    }
    return Behaviors.same();
  }

  private Behavior<DetectorCommand> onDisarm() {
    if (detectorState == DetectorState.Alarm) {
      detectorState = DetectorState.Capturing;
      logState(getContext(), detectorState);
      detectorService.alarmOff(getContext());
      detectorService.cameraCapture(getContext(), cameraId);
    }
    return Behaviors.same();
  }

  private Behavior<DetectorCommand> onInvocationFailure(GlobalCommands.InvocationFailure invocationFailure) {
    getContext().getLog().error("Service Invocation of service {} failed", invocationFailure.serviceName());
    return Behaviors.same();
  }

  public enum DetectorState {
    Capturing,
    Processing,
    Alarm
  }

  public interface DetectorCommand extends Command {}

  public record CapturedImage(byte[] image) implements DetectorCommand {

      @JsonCreator
      public CapturedImage(@JsonProperty("image") byte[] image) {
        this.image = image;
      }
    }

  public record DetectedPersons(byte[] image, Boolean hasDetectedPersons) implements DetectorCommand {

      @JsonCreator
      public DetectedPersons(
              @JsonProperty("image") byte[] image,
              @JsonProperty("hasDetectedPersons") Boolean hasDetectedPersons
      ) {
        this.image = image;
        this.hasDetectedPersons = hasDetectedPersons;
      }
    }

  public static class Timeout implements DetectorCommand {}
}
