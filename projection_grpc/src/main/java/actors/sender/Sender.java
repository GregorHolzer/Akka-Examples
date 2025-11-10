package actors.sender;

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

import java.util.*;

public class Sender extends EventSourcedBehavior<Sender.SenderCommand, Sender.SenderEvent, Sender.SenderState> {

    public static final List<String> tags =
            List.of("send_echo");

    public interface SenderCommand extends Command{}

    public interface SenderEvent extends Event{}

    public static class SendEchoEvent implements SenderEvent{

        public final String msg;

        @JsonCreator
        public SendEchoEvent(@JsonProperty("msg") String msg) {
            this.msg = msg;
        }
    }

    public static class SenderState implements State{}

    public static Behavior<SenderCommand> create(PersistenceId persistenceId) {
        return Behaviors.setup(context -> {return new Sender(persistenceId);});
    }

    private Sender(PersistenceId persistenceId){
        super(persistenceId);
    }

    @Override
    public SenderState emptyState() {
        return null;
    }

    @Override
    public CommandHandler<SenderCommand, SenderEvent, SenderState> commandHandler() {
        return null;
    }

    @Override
    public EventHandler<SenderState, SenderEvent> eventHandler() {
        return null;
    }

    @Override
    public Set<String> tagsFor(SenderEvent event) {
        return new HashSet<>(tags);
    }
}
