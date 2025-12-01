package actors.Detector;

import actors.Command;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.*;
import services.SurveillanceServices;

import java.time.Duration;

public class Detector extends AbstractBehavior<Detector.DetectorCommand> {

    public enum DetectorState {
        Capturing,
        Processing,
        Alarm
    }

    public interface DetectorCommand extends Command {}

    public static class CapturedImage implements DetectorCommand{}

    public static class Disarm implements DetectorCommand{}

    public static class DetectedPersons implements DetectorCommand{}

    public static class Alarm implements DetectorCommand{}

    public static class Timeout implements DetectorCommand{}

    public static class ImageWrapper{

        public byte[] image;

        public Boolean hasDetectedPersons = false;

        public ImageWrapper(byte[] image) {
            this.image = image;
        }
    }

    private static final Object TIMEOUT_KEY = new Object();

    private final TimerScheduler<DetectorCommand> timers;

    private final String cameraId;

    private DetectorState detectorState = DetectorState.Capturing;

    private final ImageWrapper imageWrapper = new ImageWrapper(new byte[0]);

    public static Behavior<DetectorCommand> create(String cameraId) {
        return Behaviors.withTimers(timer -> Behaviors.setup(context ->
                new Detector(context, timer, cameraId)));
    }

    public Detector(ActorContext<DetectorCommand> context, TimerScheduler<DetectorCommand> timers, String cameraId) {
        super(context);
        this.timers = timers;
        this.cameraId = cameraId;
    }

    @Override
    public Receive<DetectorCommand> createReceive() {
        SurveillanceServices.cameraCapture(getContext(), cameraId, imageWrapper);
        return newReceiveBuilder()
                .onMessage(CapturedImage.class, msg -> onCapturedImage())
                .build();
    }

    private Behavior<DetectorCommand> onCapturedImage() {
        if(detectorState == DetectorState.Capturing) {
            detectorState = DetectorState.Processing;
            SurveillanceServices.detectPersons(getContext(), imageWrapper);
            timers.startSingleTimer(TIMEOUT_KEY, new Timeout(), Duration.ofMillis(500));
            return newReceiveBuilder()
                    .onMessage(Timeout.class, msg -> onTimeout())
                    .onMessage(Alarm.class, msg -> onAlarm())
                    .onMessage(DetectedPersons.class, msg -> onDetectedPersons())
                    .build();
        }
        return Behaviors.same();
    }

    private Behavior<DetectorCommand> onDetectedPersons() {
        if (detectorState == DetectorState.Processing) {
            if(imageWrapper.hasDetectedPersons) {
                //raise foundPerson event
            }
            return newReceiveBuilder()
                    .onMessage(Timeout.class, msg -> onTimeout())
                    .onMessage(Alarm.class, msg -> onAlarm())
                    .build();
        }
        return Behaviors.same();
    }

    private Behavior<DetectorCommand> onTimeout(){
        if(detectorState == DetectorState.Processing) {
            detectorState = DetectorState.Capturing;
            SurveillanceServices.cameraCapture(getContext(), cameraId, imageWrapper);
            return newReceiveBuilder()
                    .onMessage(CapturedImage.class, msg -> onCapturedImage())
                    .build();
        }
        return Behaviors.same();
    }

    private Behavior<DetectorCommand> onAlarm() {
        if(detectorState == DetectorState.Processing) {
            timers.cancel(TIMEOUT_KEY);
            detectorState = DetectorState.Alarm;
            SurveillanceServices.alarmOn(getContext());
            return newReceiveBuilder()
                    .onMessage(Disarm.class, msg -> onDisarm())
                    .build();
        }
        return Behaviors.same();
    }

    private Behavior<DetectorCommand> onDisarm() {
        if(detectorState == DetectorState.Alarm) {
            detectorState = DetectorState.Capturing;
            SurveillanceServices.alarmOff(getContext());
            SurveillanceServices.cameraCapture(getContext(), cameraId, imageWrapper);
            return newReceiveBuilder()
                    .onMessage(CapturedImage.class, msg -> onCapturedImage())
                    .build();
        }
        return Behaviors.same();
    }
}