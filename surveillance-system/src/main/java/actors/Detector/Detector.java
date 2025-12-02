package actors.Detector;

import actors.Command;
import actors.Surveillance.Surveillance;
import actors.global_commands.GlobalCommands;
import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.internal.receptionist.ReceptionistMessages;
import akka.actor.typed.javadsl.*;
import akka.actor.typed.receptionist.Receptionist;
import akka.actor.typed.receptionist.ServiceKey;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Duration;
import services.DetectorServices;

public class Detector extends AbstractBehavior<Detector.DetectorCommand> {

  public static final ServiceKey<DetectorCommand> receptionist_detector_key = ServiceKey.create(
    DetectorCommand.class,
    "GLOBAL_DETECTOR_KEY"
  );

  public enum DetectorState {
    Capturing,
    Processing,
    Alarm
  }

  public interface DetectorCommand extends Command {}

  public static class CapturedImage implements DetectorCommand {

    public final ImageWrapper wrapper;

    @JsonCreator
    public CapturedImage(@JsonProperty("wrapper") ImageWrapper wrapper) {
      this.wrapper = wrapper;
    }
  }

  public static class DetectedPersons implements DetectorCommand {

    public final ImageWrapper wrapper;

    @JsonCreator
    public DetectedPersons(@JsonProperty("wrapper") ImageWrapper wrapper) {
      this.wrapper = wrapper;
    }
  }

  public static class Timeout implements DetectorCommand {}

  public static class ImageWrapper {

    public byte[] image;

    public Boolean hasDetectedPersons = false;

    public ImageWrapper(byte[] image) {
      this.image = image;
    }
  }

  private static final Object TIMEOUT_KEY = new Object();

  private final TimerScheduler<DetectorCommand> timers;

  private final DetectorServices detectorServices;

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

  public static Behavior<DetectorCommand> create(
    String cameraId,
    ActorRef<Surveillance.SurveillanceCommand> surveillanceActorRef,
    DetectorServices detectorServices
  ) {
    return Behaviors.withTimers(timer ->
      Behaviors.setup(context ->
        new Detector(context, timer, detectorServices, cameraId, surveillanceActorRef)
      )
    );
  }

  public Detector(
    ActorContext<DetectorCommand> context,
    TimerScheduler<DetectorCommand> timers,
    DetectorServices detectorServices,
    String cameraId,
    ActorRef<Surveillance.SurveillanceCommand> surveillanceActorRef
  ) {
    super(context);
    this.timers = timers;
    this.detectorServices = detectorServices;
    this.cameraId = cameraId;
    this.surveillanceActorRef = surveillanceActorRef;
    //register to receive global messages like Alarm or Disarm
    getContext()
      .getSystem()
      .receptionist()
      .tell(Receptionist.register(receptionist_detector_key, getContext().getSelf()));
  }

  @Override
  public Receive<DetectorCommand> createReceive() {
    ImageWrapper wrapper = new ImageWrapper(new byte[0]);
    detectorServices.cameraCapture(getContext(), cameraId, wrapper);
    return capturingBehaviour;
  }

  private Behavior<DetectorCommand> onCapturedImage(CapturedImage capturedImage) {
    if (detectorState == DetectorState.Capturing) {
      detectorState = DetectorState.Processing;
      detectorServices.detectPersons(getContext(), capturedImage.wrapper);
      timers.startSingleTimer(TIMEOUT_KEY, new Timeout(), Duration.ofMillis(500));
      return processingBehaviour;
    }
    return Behaviors.same();
  }

  private Behavior<DetectorCommand> onDetectedPersons(DetectedPersons detectedPersons) {
    if (detectorState == DetectorState.Processing) {
      if (detectedPersons.wrapper.hasDetectedPersons) {
        surveillanceActorRef.tell(new Surveillance.FoundPersons(detectedPersons.wrapper.image));
      }
      return processingBehaviour;
    }
    return Behaviors.same();
  }

  private Behavior<DetectorCommand> onTimeout() {
    if (detectorState == DetectorState.Processing) {
      detectorState = DetectorState.Capturing;
      ImageWrapper wrapper = new ImageWrapper(new byte[0]);
      detectorServices.cameraCapture(getContext(), cameraId, wrapper);
      return capturingBehaviour;
    }
    return Behaviors.same();
  }

  private Behavior<DetectorCommand> onAlarm() {
    if (detectorState == DetectorState.Processing) {
      timers.cancel(TIMEOUT_KEY);
      detectorState = DetectorState.Alarm;
      detectorServices.alarmOn(getContext());
      return alarmBehaviour;
    }
    return Behaviors.same();
  }

  private Behavior<DetectorCommand> onDisarm() {
    if (detectorState == DetectorState.Alarm) {
      detectorState = DetectorState.Capturing;
      detectorServices.alarmOff(getContext());
      ImageWrapper wrapper = new ImageWrapper(new byte[0]);
      detectorServices.cameraCapture(getContext(), cameraId, wrapper);
      return capturingBehaviour;
    }
    return Behaviors.same();
  }
}
