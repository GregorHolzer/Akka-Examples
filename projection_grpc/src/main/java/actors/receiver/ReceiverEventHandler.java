package actors.receiver;
import actors.sender.Sender;
import akka.Done;
import akka.actor.typed.ActorRef;
import akka.persistence.query.typed.EventEnvelope;
import akka.projection.javadsl.Handler;
import echo.EchoMessage;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class ReceiverEventHandler extends Handler<EventEnvelope<EchoMessage>> {

    private final ActorRef<Receiver.ReceiverCommand> receiver;

    public ReceiverEventHandler(ActorRef<Receiver.ReceiverCommand> receiver) {
        this.receiver = receiver;
    }


    @Override
    public CompletionStage<Done> process(EventEnvelope<EchoMessage> envelope) throws Exception {
        receiver.tell(new Receiver.CommandEcho(envelope.getEvent().getPayload()));
        return CompletableFuture.completedStage(Done.getInstance());
    }
}
