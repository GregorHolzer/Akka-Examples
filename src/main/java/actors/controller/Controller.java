package actors.controller;

import actors.Command;

import actors.Event;
import actors.gate.Gate;
import actors.light_machine.LightMachine;
import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.persistence.typed.PersistenceId;
import akka.persistence.typed.javadsl.*;
import java.util.List;

public class Controller extends EventSourcedBehavior<Controller.ControllerCommand, Controller.ControllerEvent, ControllerState> {

    public interface ControllerCommand extends Command {}

    public static class TrainSeen implements ControllerCommand {}

    public static class TrainNotSeen implements ControllerCommand {}

    public interface ControllerEvent extends Event{}

    public static class AdvanceState implements ControllerEvent {}

    public static class RaiseApproaching implements ControllerEvent {}

    public static class RaiseLeaving implements ControllerEvent {}

    private final ActorContext<ControllerCommand> context;

    private final ActorRef<LightMachine.LightMachineCommand> lightMachine;

    private final ActorRef<Gate.GateCommand> gate;

    public static Behavior<ControllerCommand> create(PersistenceId persistenceId) {
        return Behaviors.setup(context -> {
            String lightMachineName = String.format("lightMachine_%s", context.getSelf().path().name());
            String gateName = String.format("gateName_%s", context.getSelf().path().name());


            ActorRef<LightMachine.LightMachineCommand> lightMachine = context.spawn(LightMachine.create(
                    PersistenceId.ofUniqueId(lightMachineName)), lightMachineName);

            ActorRef<Gate.GateCommand> gate = context.spawn(Gate.create(PersistenceId.ofUniqueId(gateName)), gateName);
            return new Controller(persistenceId, context, lightMachine, gate);
        });
    }

    private Controller(PersistenceId persistenceId,
                       ActorContext<ControllerCommand> context,
                       ActorRef<LightMachine.LightMachineCommand> lightMachine,
                       ActorRef<Gate.GateCommand> gate) {
        super(persistenceId);
        this.context = context;
        this.lightMachine = lightMachine;
        this.gate = gate;
    }

    @Override
    public ControllerState emptyState() {
        return new ControllerState(ControllerState.State.AWAY);
    }

    @Override
    public CommandHandler<ControllerCommand, ControllerEvent, ControllerState> commandHandler() {
        CommandHandlerBuilder<ControllerCommand, ControllerEvent, ControllerState> builder = newCommandHandlerBuilder();

        //Handle Away State
        builder.forState(state -> state.getState() == ControllerState.State.AWAY)
                .onCommand(TrainSeen.class, cmd ->
                        Effect().persist(List.of(new AdvanceState(), new RaiseApproaching())));

        //Handle Present State
        builder.forState(state -> state.getState() == ControllerState.State.PRESENT)
                .onCommand(TrainNotSeen.class, cmd ->
                        Effect().persist(List.of(new AdvanceState(), new RaiseLeaving())));

        //Handle even States (Close, Leaving)
        builder.forState(state -> state.getState().ordinal() % 2 == 0)
                .onCommand(TrainSeen.class, cmd ->
                        Effect().persist(new AdvanceState()));

        //Handle odd States (Approaching, Left)
        builder.forState(state -> state.getState().ordinal() % 2 != 0)
                .onCommand(TrainNotSeen.class, cmd ->
                        Effect().persist(new AdvanceState()));

        builder.forAnyState()
                .onAnyCommand(cmd -> Effect().none());

        return builder.build();
    }

    @Override
    public EventHandler<ControllerState, ControllerEvent> eventHandler() {
        return newEventHandlerBuilder()
                .forAnyState()
                .onEvent(AdvanceState.class, (state,event)
                        -> (ControllerState) state.advanceState())
                .onEvent(RaiseApproaching.class, (state,event) -> {
                    lightMachine.tell(new LightMachine.TurnOn());
                    gate.tell(new Gate.GateCommandClose());
                    context.getLog().info("Sent Approaching Command");
                    return new ControllerState(state.getState());
                })
                .onEvent(RaiseLeaving.class, (state,event) -> {
                    lightMachine.tell(new LightMachine.TurnOff());
                    gate.tell(new Gate.GateCommandOpen());
                    context.getLog().info("Sent Leaving Command");
                    return new ControllerState(state.getState());
                })
                .onEvent(ControllerEvent.class, (state, event) -> new ControllerState(state.getState()))
                .build();
    }
}