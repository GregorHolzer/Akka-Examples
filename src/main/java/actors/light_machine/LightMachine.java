package actors.light_machine;

import actors.controller.events.ControllerEvent;
import actors.light_machine.commands.LightMachineCommand;
import actors.light_machine.commands.LightMachineCommandTurnOff;
import actors.light_machine.commands.LightMachineCommandTurnOn;
import actors.light_machine.events.LightMachineEvent;
import actors.light_machine.events.LightMachineEventAdvanceState;
import actors.light_machine.events.LightMachineEventTurnOff;
import actors.light_machine.events.LightMachineEventTurnOn;
import akka.actor.typed.Behavior;
import akka.persistence.typed.PersistenceId;
import akka.persistence.typed.javadsl.CommandHandler;
import akka.persistence.typed.javadsl.CommandHandlerBuilder;
import akka.persistence.typed.javadsl.EventHandler;
import akka.persistence.typed.javadsl.EventSourcedBehavior;
import akka.persistence.typed.scaladsl.Effect;

import java.util.List;

public class LightMachine extends EventSourcedBehavior<LightMachineCommand, LightMachineEvent, LightMachineState> {

    public static Behavior<LightMachineCommand> create(PersistenceId persistenceId) {
        return new  LightMachine(persistenceId);
    }

    private LightMachine(PersistenceId persistenceId) {
        super(persistenceId);
    }

    @Override
    public LightMachineState emptyState() {
        return new LightMachineState(LightMachineState.State.OFF);
    }

    @Override
    public CommandHandler<LightMachineCommand, LightMachineEvent, LightMachineState> commandHandler() {
        CommandHandlerBuilder<LightMachineCommand, LightMachineEvent, LightMachineState> builder = newCommandHandlerBuilder();

        builder.forState(state -> state.getState() ==  LightMachineState.State.OFF)
                .onCommand(LightMachineCommandTurnOn.class, cmd ->  Effect().persist(
                        List.of(new LightMachineEventAdvanceState(), new LightMachineEventTurnOn())
                ));

        builder.forState(state -> state.getState() ==  LightMachineState.State.ON)
                .onCommand(LightMachineCommandTurnOff.class, cmd ->  Effect().persist(
                        List.of(new LightMachineEventAdvanceState(), new  LightMachineEventTurnOff())
                ));

        builder.forAnyState().onAnyCommand(cmd -> Effect().none());

        return builder.build();
    }

    @Override
    public EventHandler<LightMachineState, LightMachineEvent> eventHandler() {
        return newEventHandlerBuilder()
                .forAnyState()
                .onEvent(LightMachineEventAdvanceState.class, (state, event)
                        -> (LightMachineState) state.advanceState())
                .onEvent(LightMachineEvent.class, (state,event) -> new LightMachineState(
                        state.getState()
                ))
                .build();
    }
}