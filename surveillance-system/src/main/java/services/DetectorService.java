package services;

import actors.Detector;
import akka.actor.typed.javadsl.ActorContext;

//DummyService
public class DetectorService extends AkkaService{

  private Integer counter = 0;

  public DetectorService(String ip, Integer port) {
    super(ip, port);
  }

  public void alarmOn(ActorContext<Detector.DetectorCommand> context) {
      sendRequest(context, buildPostRequest("/alarm/on"));
  }

  public void alarmOff(ActorContext<Detector.DetectorCommand> context) {
     sendRequest(context, buildPostRequest("/alarm/off"));
  }

  public void cameraCapture(ActorContext<Detector.DetectorCommand> context, String cameraId) {
      context.getLog().info("cameraCapture({})", cameraId);
    counter++;
    context.getSelf().tell(new Detector.CapturedImage(new byte[0]));
  }

  public void detectPersons(
    ActorContext<Detector.DetectorCommand> context,
    Detector.CapturedImage capturedImage
  ) {
    context.getLog().info("detectPersons: detectedPersons = {}", counter % 2 == 0);
    context.getSelf().tell(new Detector.DetectedPersons(capturedImage.image(), counter % 2 == 0));
  }
}
