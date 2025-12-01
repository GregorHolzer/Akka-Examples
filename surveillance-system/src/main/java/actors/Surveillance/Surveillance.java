package actors.Surveillance;

import actors.Command;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import services.SurveillanceServices;

public class Surveillance extends AbstractBehavior<Surveillance.SurveillanceCommand> {

    public enum SurveillanceState{
        Processing,
        Alarm
    }

    public interface  SurveillanceCommand extends Command {}

    private static class FoundPersons implements SurveillanceCommand{
        public byte[] image;

        @JsonCreator
        public FoundPersons(@JsonProperty("image") byte[] image){
            this.image = image;
        }
    }

    public static class Analyzed implements SurveillanceCommand {}

    private Surveillance(ActorContext<Surveillance.SurveillanceCommand> context) {
        super(context);
    }

    public static class ImageWrapper {

        public byte[] image;

        public Boolean hasThread;

        public ImageWrapper(byte[] image, Boolean hasThread){
            this.image = image;
            this.hasThread = hasThread;
        }
    }

    private ImageWrapper imageWrapper = new ImageWrapper(new byte[0], false);

    private SurveillanceState surveillanceState = SurveillanceState.Processing;

    @Override
    public Receive<SurveillanceCommand> createReceive(){
        return newReceiveBuilder()
                .onMessage(FoundPersons.class, this::onFoundPersons)
                .build();
    }

    private Behavior<SurveillanceCommand> onFoundPersons(FoundPersons persons){
        if(surveillanceState == SurveillanceState.Processing){
            imageWrapper.image = persons.image;
            SurveillanceServices.analyze(getContext(),imageWrapper);
            return newReceiveBuilder()
                    .onMessage(Analyzed.class, msg -> onAnalyzed())
                    .build();
        }
        return Behaviors.same();
    }

    private Behavior<SurveillanceCommand> onAnalyzed(){
        if(surveillanceState == SurveillanceState.Processing){
            if(imageWrapper.hasThread){
                surveillanceState = SurveillanceState.Alarm;
            }
            else {

            }
        }
        return Behaviors.same();
    }

    private Behavior<SurveillanceCommand> onAlarm(){

    }
}