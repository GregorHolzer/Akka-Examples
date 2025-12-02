package services;

import actors.Detector;
import akka.actor.typed.javadsl.ActorContext;

//DummyService
public class DetectorServices {

  private Integer counter = 0;

  public void alarmOn(ActorContext<?> context) {
    context.getLog().info("SurveillanceServices.alarmOn");
  }

  public void alarmOff(ActorContext<?> context) {
    context.getLog().info("SurveillanceServices.alarmOff");
  }

  public void cameraCapture(ActorContext<Detector.DetectorCommand> context, String cameraId) {
    System.out.println("SurveillanceServices.cameraCapture( " + cameraId + ",wrapper)");
    counter++;
    context.getSelf().tell(new Detector.CapturedImage(new byte[0]));
  }

  public void detectPersons(
    ActorContext<Detector.DetectorCommand> context,
    Detector.CapturedImage capturedImage
  ) {
    context.getLog().info("SurveillanceServices.detectPersons");
    context.getSelf().tell(new Detector.DetectedPersons(capturedImage.image, counter % 2 == 0));
  }
}
