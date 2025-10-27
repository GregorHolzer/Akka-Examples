package actors;

import actors.messages.Approaching;
import actors.messages.ControllerMessage;
import actors.messages.Leaving;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;

/// Represents the Gate of a Railway-Crossing
/// The Parent-Actor is the {@link Controller} within the same Railway-Crossing
public class Gate extends AbstractBehavior<ControllerMessage> implements StateMachine<Gate.GateState> {

    enum GateState {
        UP,
        DOWN,
    }

    @Override
    public Receive<ControllerMessage> createReceive() {
        return newReceiveBuilder()
                .onMessage(Approaching.class, msg -> down())
                .onMessage(Leaving.class, msg -> up())
                .build();
    }

    public static Behavior<ControllerMessage> create() {
        return Behaviors.setup(Gate::new);
    }

    public Gate(ActorContext<ControllerMessage> context) {
        super(context);
    }

    /// Models Up-State according to Gate State Machine
    public Behavior<ControllerMessage> up() {
        logStateToConsole(GateState.UP, getContext());
        //TODO: invoke Up-Service, maybe store Log persistent
        return Behaviors.receive(ControllerMessage.class)
                .onMessage(Approaching.class, msg -> down())
                .onMessage(Leaving.class, msg -> Behaviors.same())
                .build();
    }

    /// Models Down-State according to Gate State Machine
    public Behavior<ControllerMessage> down() {
        logStateToConsole(GateState.DOWN, getContext());
        //TODO: invoke Down-Service, maybe store Log persistent
        return Behaviors.receive(ControllerMessage.class)
                .onMessage(Approaching.class, msg -> Behaviors.same())
                .onMessage(Leaving.class, msg -> up())
                .build();
    }
}