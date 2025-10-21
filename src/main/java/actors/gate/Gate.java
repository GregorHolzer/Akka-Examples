package actors.gate;

import actors.Command;
import actors.Event;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.persistence.typed.PersistenceId;
import akka.persistence.typed.javadsl.CommandHandler;
import akka.persistence.typed.javadsl.CommandHandlerBuilder;
import akka.persistence.typed.javadsl.EventHandler;
import akka.persistence.typed.javadsl.EventSourcedBehavior;
import java.util.List;

public class Gate extends EventSourcedBehavior<Gate.GateCommand, Gate.GateEvent, GateState> {

    public interface GateCommand extends Command {}

    public static class GateCommandOpen implements GateCommand {}

    public static class GateCommandClose implements GateCommand {}

    public interface GateEvent extends Event {}

    public static class GateEventOpened implements GateEvent {}

    public static class GateEventClosed implements GateEvent {}

    public static class GateEventAdvanceState implements GateEvent {}

    private final ActorContext<GateCommand> context;

    public static Behavior<GateCommand> create(PersistenceId persistenceId) {
        return Behaviors.setup(context -> new Gate(persistenceId, context));
    }

    private Gate(PersistenceId persistenceId, ActorContext<GateCommand> context) {
        super(persistenceId);
        this.context = context;
    }

    @Override
    public GateState emptyState() {
        return new GateState(GateState.State.OPEN);
    }

    @Override
    public CommandHandler<GateCommand, GateEvent, GateState> commandHandler() {
        CommandHandlerBuilder<GateCommand, GateEvent, GateState> builder = newCommandHandlerBuilder();

        builder.forState(state -> state.getState() == GateState.State.OPEN)
                .onCommand(GateCommandClose.class, cmd -> Effect().persist(
                        List.of(new GateEventAdvanceState(), new GateEventClosed())
                ));

        builder.forState(state -> state.getState() == GateState.State.CLOSED)
                .onCommand(GateCommandOpen.class, cmd -> Effect().persist(
                        List.of(new GateEventAdvanceState(), new GateEventOpened())
                ));

        builder.forAnyState().onAnyCommand(cmd -> Effect().none());

        return builder.build();
    }

    @Override
    public EventHandler<GateState, GateEvent> eventHandler() {
        return newEventHandlerBuilder()
                .forAnyState()
                .onEvent(GateEventAdvanceState.class, (state,event) -> {
                    context.getLog().info("Gate Advance State to {}", state.advanceState().getState());
                    return (GateState) state.advanceState();
                })
                .onEvent(GateEvent.class, (state, gate) -> state.createWithState(state.getState()))
                .build();
    }
}