package actors.sender;

import actors.Command;
import actors.Event;
import actors.State;

import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.persistence.typed.PersistenceId;
import akka.persistence.typed.javadsl.*;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.*;

public class Sender extends EventSourcedBehavior<Sender.SenderCommand, Sender.SenderEvent, Sender.SenderState> {

    public static final List<String> tags =
            List.of("send_echo");

    public interface SenderCommand extends Command{}

    public static class SendMessage implements SenderCommand {}

    public interface SenderEvent extends Event{}

    public static class SendEchoEvent implements SenderEvent{

        public final String msg;

        @JsonCreator
        public SendEchoEvent(@JsonProperty("msg") String msg) {
            this.msg = msg;
        }
    }

    public static class SenderState implements State{

        public SenderState(){

        }

    }

    public static Behavior<SenderCommand> create(PersistenceId persistenceId) {
        return Behaviors.setup(context -> {return new Sender(persistenceId, context);});
    }

    private final ActorContext<SenderCommand> context;

    private Sender(PersistenceId persistenceId, ActorContext<SenderCommand> context) {
        super(persistenceId);
        this.context = context;
    }

    @Override
    public SenderState emptyState() {
        return new SenderState();
    }

    @Override
    public CommandHandler<SenderCommand, SenderEvent, SenderState> commandHandler() {
        CommandHandlerBuilder<SenderCommand, SenderEvent, SenderState> builder = newCommandHandlerBuilder();

        builder.forAnyState().onCommand(SendMessage.class, cmd -> Effect().persist(new SendEchoEvent("Hello from Sender"))
                .thenRun(() -> {
                    context.getLog().info("Received Command: SendMessage");
                })
        );

        return builder.build();
    }

    @Override
    public EventHandler<SenderState, SenderEvent> eventHandler() {
        EventHandlerBuilder<SenderState, SenderEvent> builder = newEventHandlerBuilder();

        builder.forAnyState().onEvent(SendEchoEvent.class, (state, event) -> {
            context.getLog().info("Persisted Event: SendEchoEvent");
            return emptyState();
        });

        return builder.build();
    }

    @Override
    public Set<String> tagsFor(SenderEvent event) {
        return new HashSet<>(tags);
    }
}
