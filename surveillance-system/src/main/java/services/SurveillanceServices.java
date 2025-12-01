package services;

import actors.Detector.Detector;
import actors.Surveillance.Surveillance;
import akka.actor.typed.javadsl.ActorContext;


public class SurveillanceServices {

    private static Integer counter = 0;

    //Dummy Services
    public static void alarmOn(ActorContext<?> context){
        context.getLog().info("SurveillanceServices.alarmOn");
    }

    public static void alarmOff(ActorContext<?> context){
        context.getLog().info("SurveillanceServices.alarmOff");
    }

    public static void cameraCapture(ActorContext<Detector.DetectorCommand> context, String cameraId, Detector.ImageWrapper wrapper){
        System.out.println("SurveillanceServices.cameraCapture( " + cameraId + ",wrapper)");
        counter++;
        byte[] newImage = new byte[1];
        newImage[0] = counter.byteValue();
        wrapper.image = newImage;
        context.getSelf().tell(new Detector.CapturedImage());
    }

    public static void detectPersons(ActorContext<Detector.DetectorCommand> context, Detector.ImageWrapper wrapper){
        context.getLog().info("SurveillanceServices.detectPersons");
        wrapper.hasDetectedPersons = (counter % 2 == 0);
        context.getSelf().tell(new Detector.DetectedPersons());
    }

    public static void analyze(ActorContext<Surveillance.SurveillanceCommand> context, Surveillance.ImageWrapper wrapper){
        context.getLog().info("SurveillanceServices.analyze()");
        wrapper.hasThread = (counter % 2 == 0);
        context.getSelf().tell(new Surveillance.Analyzed());
    }
}
