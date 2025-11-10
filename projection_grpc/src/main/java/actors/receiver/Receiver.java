package actors.receiver;

import actors.Command;
import actors.Event;
import actors.State;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.Behaviors;
import akka.persistence.typed.PersistenceId;
import akka.persistence.typed.javadsl.CommandHandler;
import akka.persistence.typed.javadsl.EventHandler;
import akka.persistence.typed.javadsl.EventSourcedBehavior;
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

    public static class ReceiverState implements State {}

    @Override
    public ReceiverState emptyState() {
        return null;
    }

    public static Behavior<ReceiverCommand> create(PersistenceId persistenceId) {
        return Behaviors.setup(context -> {return Receiver.create(persistenceId);});
    }

    private Receiver(PersistenceId persistenceId) {
        super(persistenceId);
    }

    @Override
    public CommandHandler<ReceiverCommand, ReceiverEvent, ReceiverState> commandHandler() {
        return null;
    }

    @Override
    public EventHandler<ReceiverState, ReceiverEvent> eventHandler() {
        return null;
    }

}
