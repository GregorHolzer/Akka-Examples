package actors;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.*;
import akka.actor.typed.receptionist.Receptionist;
import akka.actor.typed.receptionist.ServiceKey;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Duration;
import services.DetectorService;

public class Detector extends AbstractBehavior<Detector.DetectorCommand> implements StateMachine<Detector.DetectorState>{

  private static final Object TIMEOUT_KEY = new Object();

  private final TimerScheduler<DetectorCommand> timers;

  private final DetectorService detectorService;

  private final Receive<DetectorCommand> capturingBehaviour = newReceiveBuilder()
    .onMessage(CapturedImage.class, this::onCapturedImage)
    .build();

  private final Receive<DetectorCommand> processingBehaviour = newReceiveBuilder()
    .onMessage(DetectedPersons.class, this::onDetectedPersons)
    .onMessage(Timeout.class, msg -> onTimeout())
    .onMessage(GlobalCommands.Alarm.class, msg -> onAlarm())
    .build();

  private final Receive<DetectorCommand> alarmBehaviour = newReceiveBuilder()
    .onMessage(GlobalCommands.Disarm.class, msg -> onDisarm())
    .build();

  private final String cameraId;

  private final ActorRef<Surveillance.SurveillanceCommand> surveillanceActorRef;

  private DetectorState detectorState = DetectorState.Capturing;

  public Detector(
    ActorContext<DetectorCommand> context,
    TimerScheduler<DetectorCommand> timers,
    DetectorService detectorService,
    String groupId,
    String cameraId,
    ActorRef<Surveillance.SurveillanceCommand> surveillanceActorRef
  ) {
    super(context);
    this.timers = timers;
    this.detectorService = detectorService;
    this.cameraId = cameraId;
    this.surveillanceActorRef = surveillanceActorRef;
    ServiceKey<DetectorCommand> groupDetectorKey = ServiceKey.create(DetectorCommand.class, groupId);
    //register to receive group messages like Alarm or Disarm
    getContext()
      .getSystem()
      .receptionist()
      .tell(Receptionist.register(groupDetectorKey, getContext().getSelf()));
    //Start Initial Invocation
      detectorService.cameraCapture(getContext(), cameraId);
  }

  public static Behavior<DetectorCommand> create(
          String groupId,
    String cameraId,
    ActorRef<Surveillance.SurveillanceCommand> surveillanceActorRef,
    DetectorService detectorService
  ) {
    return Behaviors.withTimers(timer ->
      Behaviors.setup(context ->
        new Detector(context, timer, detectorService, groupId,cameraId, surveillanceActorRef)
      )
    );
  }

  @Override
  public Receive<DetectorCommand> createReceive() {
    return capturingBehaviour;
  }

  private Behavior<DetectorCommand> onCapturedImage(CapturedImage capturedImage) {
    if (detectorState == DetectorState.Capturing) {
      detectorState = DetectorState.Processing;
        logState(getContext(), detectorState);
      detectorService.detectPersons(getContext(), capturedImage);
      timers.startSingleTimer(TIMEOUT_KEY, new Timeout(), Duration.ofMillis(500));
      return processingBehaviour;
    }
    return Behaviors.same();
  }

  private Behavior<DetectorCommand> onDetectedPersons(DetectedPersons detectedPersons) {
    if (detectorState == DetectorState.Processing) {
      if (detectedPersons.hasDetectedPersons) {
        surveillanceActorRef.tell(new Surveillance.FoundPersons(detectedPersons.image));
      }
      return processingBehaviour;
    }
    return Behaviors.same();
  }

  private Behavior<DetectorCommand> onTimeout() {
    if (detectorState == DetectorState.Processing) {
      detectorState = DetectorState.Capturing;
        logState(getContext(), detectorState);
      detectorService.cameraCapture(getContext(), cameraId);
      return capturingBehaviour;
    }
    return Behaviors.same();
  }

  private Behavior<DetectorCommand> onAlarm() {
    if (detectorState == DetectorState.Processing) {
      timers.cancel(TIMEOUT_KEY);
      detectorState = DetectorState.Alarm;
        logState(getContext(), detectorState);
      detectorService.alarmOn(getContext());
      return alarmBehaviour;
    }
    return Behaviors.same();
  }

  private Behavior<DetectorCommand> onDisarm() {
    if (detectorState == DetectorState.Alarm) {
      detectorState = DetectorState.Capturing;
      logState(getContext(), detectorState);
      detectorService.alarmOff(getContext());
      detectorService.cameraCapture(getContext(), cameraId);
      return capturingBehaviour;
    }
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
