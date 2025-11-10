package actors.receiver;
import actors.sender.Sender;
import akka.Done;
import akka.actor.typed.ActorRef;
import akka.projection.eventsourced.EventEnvelope;
import akka.projection.javadsl.Handler;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class ReceiverEventHandler extends Handler<EventEnvelope<Sender.SenderEvent>> {

    private final ActorRef<Receiver.ReceiverCommand> receiver;

    public ReceiverEventHandler(ActorRef<Receiver.ReceiverCommand> receiver) {
        this.receiver = receiver;
    }


    @Override
    public CompletionStage<Done> process(EventEnvelope<Sender.SenderEvent> senderEventEventEnvelope) throws Exception {
        Sender.SenderEvent senderEvent = senderEventEventEnvelope.event();
        if (senderEvent instanceof Sender.SendEchoEvent echoEvent){
            receiver.tell(new Receiver.CommandEcho(echoEvent.msg));
        }
        return CompletableFuture.completedStage(Done.getInstance());
    }
}
