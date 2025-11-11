package actors.receiver;

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

public class Receiver extends EventSourcedBehavior<Receiver.ReceiverCommand, Receiver.ReceiverEvent, Receiver.ReceiverState> {

    public interface ReceiverCommand extends Command {}

    public static class CommandEcho implements ReceiverCommand {
        public final String msg;

        @JsonCreator
        public CommandEcho(@JsonProperty("msg") String msg) {
            this.msg = msg;
        }
    }

    public interface ReceiverEvent extends Event{}

    public record EchoEvent(String msg) implements ReceiverEvent {
            @JsonCreator
            public EchoEvent(@JsonProperty("msg") String msg) {
                this.msg = msg;
            }
        }

    public static class ReceiverState implements State {

        public ReceiverState() {

        }

    }

    private final ActorContext<ReceiverCommand> context;

    @Override
    public ReceiverState emptyState() {
        return new ReceiverState();
    }

    public static Behavior<ReceiverCommand> create(PersistenceId persistenceId) {
        return Behaviors.setup(context -> {return new Receiver(persistenceId, context);});
    }

    private Receiver(PersistenceId persistenceId, ActorContext<ReceiverCommand> context) {
        super(persistenceId);
        this.context = context;
    }

    @Override
    public CommandHandler<ReceiverCommand, ReceiverEvent, ReceiverState> commandHandler() {
        CommandHandlerBuilder<ReceiverCommand, ReceiverEvent, ReceiverState> builder = newCommandHandlerBuilder();

        builder.forAnyState().onCommand(CommandEcho.class, cmd -> Effect().persist(new EchoEvent(cmd.msg))
                .thenRun(() -> {
                    context.getLog().info("Received CommandEcho.class");
                }));

        return builder.build();
    }

    @Override
    public EventHandler<ReceiverState, ReceiverEvent> eventHandler() {
        EventHandlerBuilder<ReceiverState, ReceiverEvent> builder = newEventHandlerBuilder();

        builder.forAnyState().onEvent(EchoEvent.class, (state, event ) -> {
            context.getLog().info("Received EchoEvent with msg: {}", event.msg);
            return emptyState();
        });

        return builder.build();
    }

}
