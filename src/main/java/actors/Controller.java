package actors;

import actors.messages.Approaching;
import actors.messages.ControllerMessage;
import actors.messages.Leaving;
import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;

/// Represents the Controller of a Railway-Crossing
/// Creates two Child-Actors:
///         - one {@link Gate} Actor
///         - one {@link LightMachine} Actor
public class Controller extends AbstractBehavior<Controller.SensorMessage> implements StateMachine<Controller.ControllerState>{

    public interface SensorMessage {}

    public static final class TrainSeen implements SensorMessage {}

    public static final class TrainNotSeen implements SensorMessage {}

    enum ControllerState {
        AWAY,
        APPROACHING,
        CLOSE,
        PRESENT,
        LEAVING,
        LEFT
    }

    private final ActorRef<ControllerMessage> gate;

    private final ActorRef<ControllerMessage> lightMachine;

    public static Behavior<Controller.SensorMessage> create() {
        return Behaviors.setup(Controller::new);
    }

    @Override
    public Receive<SensorMessage> createReceive() {
        return newReceiveBuilder()
                .onMessage(TrainNotSeen.class, msg -> away())
                .onMessage(TrainSeen.class, msg -> approach())
                .build();
    }

    public Controller(ActorContext<SensorMessage> context) {
        super(context);
        gate = context.spawn(Gate.create(), context.getSelf().path().name() + "-" + "Gate");
        lightMachine = context.spawn(LightMachine.create(), context.getSelf().path().name() + "-" + "LightMachine");
    }

    /// Models Away-State according to Controller State Machine
    private Behavior<SensorMessage> away() {
        logStateToConsole(ControllerState.AWAY, getContext());
        return Behaviors.receive(SensorMessage.class)
                .onMessage(TrainSeen.class, msg -> approach())
                .onMessage(TrainNotSeen.class, msg -> Behaviors.same())
                .build();
    }

    /// Models Approach-State according to Controller State Machine
    private Behavior<SensorMessage> approach() {
        logStateToConsole(ControllerState.APPROACHING, getContext());
        //Send Approaching to LightMachine and RailwayGate
        gate.tell(new Approaching());
        lightMachine.tell(new Approaching());
        return Behaviors.receive(SensorMessage.class)
                .onMessage(TrainSeen.class, msg -> Behaviors.same())
                .onMessage(TrainNotSeen.class, msg -> close())
                .build();
    }

    /// Models Close-State according to Controller State Machine
    private Behavior<SensorMessage> close() {
        logStateToConsole(ControllerState.CLOSE, getContext());
        return Behaviors.receive(SensorMessage.class)
                .onMessage(TrainSeen.class, msg -> present())
                .onMessage(TrainNotSeen.class, msg -> Behaviors.same())
                .build();
    }

    /// Models Present-State according to Controller State Machine
    private Behavior<SensorMessage> present() {
        logStateToConsole(ControllerState.PRESENT, getContext());
        return Behaviors.receive(SensorMessage.class)
                .onMessage(TrainSeen.class, msg -> Behaviors.same())
                .onMessage(TrainNotSeen.class, msg -> leaving())
                .build();
    }

    /// Models Leaving-State according to Controller State Machine
    private Behavior<SensorMessage> leaving() {
        logStateToConsole(ControllerState.LEAVING, getContext());
        //Send Leaving to LightMachine and RailwayGate
        gate.tell(new Leaving());
        lightMachine.tell(new Leaving());
        return Behaviors.receive(SensorMessage.class)
                .onMessage(TrainSeen.class, msg -> left())
                .onMessage(TrainNotSeen.class, msg -> Behaviors.same())
                .build();
    }

    /// Models Left-State according to Controller State Machine
    private Behavior<SensorMessage> left() {
        logStateToConsole(ControllerState.LEFT, getContext());
        return Behaviors.receive(SensorMessage.class)
                .onMessage(TrainSeen.class, msg -> Behaviors.same())
                .onMessage(TrainNotSeen.class, msg -> away())
                .build();
    }
}