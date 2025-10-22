package actors.light_machine;

import actors.Command;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.persistence.typed.PersistenceId;
import akka.persistence.typed.javadsl.CommandHandler;
import akka.persistence.typed.javadsl.CommandHandlerBuilder;
import akka.persistence.typed.javadsl.EventHandler;
import akka.persistence.typed.javadsl.EventSourcedBehavior;
import java.util.List;

public class LightMachine extends EventSourcedBehavior<LightMachine.LightMachineCommand, LightMachine.LightMachineEvent, LightMachineState> {

    public interface LightMachineCommand extends Command {}

    public static class CommandTurnOn implements LightMachineCommand {}

    public static class CommandTurnOff implements LightMachineCommand {}

    public interface LightMachineEvent extends Command {}

    public static class EventAdvanceState implements LightMachineEvent {}

    public static class EventTurnedOn implements LightMachineEvent {}

    public static class EventTurnedOff implements LightMachineEvent {}

    private final ActorContext<LightMachineCommand> context;

    public static Behavior<LightMachineCommand> create(PersistenceId persistenceId) {
        return Behaviors.setup(context -> new  LightMachine(persistenceId, context));
    }

    private LightMachine(PersistenceId persistenceId, ActorContext<LightMachineCommand> context) {
        super(persistenceId);
        this.context = context;
    }

    @Override
    public LightMachineState emptyState() {
        return new LightMachineState(LightMachineState.State.OFF);
    }

    @Override
    public CommandHandler<LightMachineCommand, LightMachineEvent, LightMachineState> commandHandler() {
        CommandHandlerBuilder<LightMachineCommand, LightMachineEvent, LightMachineState> builder = newCommandHandlerBuilder();

        builder.forState(state -> state.getState() ==  LightMachineState.State.OFF)
                .onCommand(CommandTurnOn.class, cmd ->  Effect().persist(
                        List.of(new EventAdvanceState(), new EventTurnedOn())
                ));

        builder.forState(state -> state.getState() ==  LightMachineState.State.ON)
                .onCommand(CommandTurnOff.class, cmd ->  Effect().persist(
                        List.of(new EventAdvanceState(), new EventTurnedOff())
                ));

        builder.forAnyState().onAnyCommand(cmd -> Effect().none());

        return builder.build();
    }

    @Override
    public EventHandler<LightMachineState, LightMachineEvent> eventHandler() {
        return newEventHandlerBuilder()
                .forAnyState()
                .onEvent(EventAdvanceState.class, (state, event)
                        -> {
                    context.getLog().info("Advanced State to {}", state.advanceState().getState());
                    return (LightMachineState) state.advanceState();
                })
                .onEvent(LightMachineEvent.class, (state,event) -> new LightMachineState(
                        state.getState()
                ))
                .build();
    }
}