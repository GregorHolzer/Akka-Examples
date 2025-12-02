package services;

import actors.Detector.Detector;
import akka.actor.typed.javadsl.ActorContext;

//DummyService
public class DetectorServices {

    private Integer counter = 0;

    public void alarmOn(ActorContext<?> context){
        context.getLog().info("SurveillanceServices.alarmOn");
    }

    public void alarmOff(ActorContext<?> context){
        context.getLog().info("SurveillanceServices.alarmOff");
    }

    public void cameraCapture(ActorContext<Detector.DetectorCommand> context, String cameraId, Detector.ImageWrapper wrapper){
        System.out.println("SurveillanceServices.cameraCapture( " + cameraId + ",wrapper)");
        counter++;
        byte[] newImage = new byte[1];
        newImage[0] = counter.byteValue();
        wrapper.image = newImage;
        context.getSelf().tell(new Detector.CapturedImage(wrapper));
    }

    public void detectPersons(ActorContext<Detector.DetectorCommand> context, Detector.ImageWrapper wrapper){
        context.getLog().info("SurveillanceServices.detectPersons");
        wrapper.hasDetectedPersons = (counter % 2 == 0);
        context.getSelf().tell(new Detector.DetectedPersons(wrapper));
    }
}
