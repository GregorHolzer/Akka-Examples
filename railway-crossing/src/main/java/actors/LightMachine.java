package actors;

import actors.messages.Approaching;
import actors.messages.ControllerMessage;
import actors.messages.Leaving;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;

/// Represents the LightMachine of a Railway-Crossing
/// The Parent-Actor is the {@link Controller} within the same Railway-Crossing
public class LightMachine extends AbstractBehavior<ControllerMessage> implements StateMachine<LightMachine.LightMachineState> {

    enum LightMachineState {
        ON,
        OFF
    }

    @Override
    public Receive<ControllerMessage> createReceive() {
        return newReceiveBuilder()
                .onMessage(Approaching.class, msg -> on())
                .onMessage(Leaving.class, msg -> off())
                .build();
    }

    public static Behavior<ControllerMessage> create() {
        return Behaviors.setup(LightMachine::new);
    }

    public LightMachine(ActorContext<ControllerMessage> context) {
        super(context);
    }

    /// Models Off-State according to Light State Machine
    public Behavior<ControllerMessage> off() {
        logStateToConsole(LightMachineState.OFF, getContext());
        //TODO: invoke Off-Service, maybe store Log persistent
        return Behaviors.receive(ControllerMessage.class)
                .onMessage(Approaching.class, msg -> on())
                .onMessage(Leaving.class, msg -> Behaviors.same())
                .build();
    }

    /// Models On-State according to Light State Machine
    public Behavior<ControllerMessage> on() {
        logStateToConsole(LightMachineState.ON, getContext());
        //TODO: invoke On-Service, maybe store Log persistent
        return Behaviors.receive(ControllerMessage.class)
                .onMessage(Approaching.class, msg -> Behaviors.same())
                .onMessage(Leaving.class, msg -> off())
                .build();
    }
}
