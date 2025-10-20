package actors.controller;

import actors.controller.commands.ControllerCommand;
import actors.controller.commands.ControllerCommandTrainNotSeen;
import actors.controller.commands.ControllerCommandTrainSeen;
import actors.controller.events.*;
import akka.actor.typed.Behavior;
import akka.persistence.typed.PersistenceId;
import akka.persistence.typed.javadsl.*;
import java.util.List;
import java.util.Set;


public class Controller extends EventSourcedBehavior<ControllerCommand, ControllerEvent, ControllerState> {

    public static final String forwardTag = "Event-To-Forward";

    public static Behavior<ControllerCommand> create(PersistenceId persistenceId) {
        return new Controller(persistenceId);
    }

    private Controller(PersistenceId persistenceId) {
        super(persistenceId);
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
                .onCommand(ControllerCommandTrainSeen.class, cmd ->
                        Effect().persist(List.of(new ControllerEventAdvanceState(), new ControllerEventRaiseApproaching())));

        //Handle Present State
        builder.forState(state -> state.getState() == ControllerState.State.PRESENT)
                .onCommand(ControllerCommandTrainNotSeen.class, cmd ->
                        Effect().persist(List.of(new ControllerEventAdvanceState(), new ControllerEventRaiseLeaving())));

        //Handle even States (Close, Leaving)
        builder.forState(state -> state.getState().ordinal() % 2 == 0)
                .onCommand(ControllerCommandTrainSeen.class, cmd ->
                        Effect().persist(new ControllerEventAdvanceState()));

        //Handle odd States (Approaching, Left)
        builder.forState(state -> state.getState().ordinal() % 2 != 0)
                .onCommand(ControllerCommandTrainNotSeen.class, cmd ->
                        Effect().persist(new ControllerEventAdvanceState()));

        builder.forAnyState()
                .onAnyCommand(cmd -> Effect().none());

        return builder.build();
    }

    @Override
    public EventHandler<ControllerState, ControllerEvent> eventHandler() {
        return newEventHandlerBuilder()
                .forAnyState()
                .onEvent(ControllerEventAdvanceState.class, (state,event)
                        -> (ControllerState) state.advanceState())
                .onEvent(ControllerEvent.class, (state, event) -> new ControllerState(state.getState()))
                .build();
    }


    @Override
    public Set<String> tagsFor(ControllerEvent event) {
        if (event instanceof ControllerEventRaiseApproaching || event instanceof ControllerEventRaiseLeaving) {
            return Set.of(forwardTag);
        }
        return Set.of();
    }
}