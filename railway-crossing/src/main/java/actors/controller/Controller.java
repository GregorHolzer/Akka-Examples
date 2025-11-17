package actors.controller;

import actors.Command;
import actors.StateMachine;
import actors.api.SignalReceiver;
import actors.bell.Bell;
import actors.gate.Gate;
import actors.light_machine.LightMachine;
import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;


public class Controller
  extends AbstractBehavior<Controller.ControllerCommand>
  implements StateMachine<Controller.State> {

    /**
     * Defines States of the {@link Controller} actor
     */
  public enum State {
    Away,
    Approaching,
    Close,
    Present,
    Leaving,
    Left
  }

    /**
     * Defines the message-type {@link Controller} can receive
     */
  public interface ControllerCommand extends Command {}

    /**
     * Message that requests the name of the {@link Controller} actor
     */
  public static class CommandGetControllerName implements ControllerCommand {

    public final ActorRef<SignalReceiver.SignalReceiverCommand> replyTo;

    @JsonCreator
    public CommandGetControllerName(
      @JsonProperty("replyTo") ActorRef<SignalReceiver.SignalReceiverCommand> replyTo
    ) {
      this.replyTo = replyTo;
    }
  }

    /**
     * Message that indicates that a sensor detects a train
     */
  public static class CommandTrainSeen implements ControllerCommand {}

    /**
     * Message that indicates that a sensor no longer detects a train
     */
  public static class CommandTrainNotSeen implements ControllerCommand {}

  private final ActorRef<LightMachine.LightMachineCommand> lightMachine;

  private final ActorRef<Gate.GateCommand> gate;

  private State state = State.Away;

    /**
     * Creates a new {@link Controller} actor.
     *
     * @param gate {@link ActorRef} of the {@link Gate} actor that belongs to the same crossing
     * @param lightMachine {@link ActorRef} of the {@link LightMachine} actor that belongs to the same crossing
     * @return a new {@link Behavior} instance for the {@link Bell} actor
     */
  public static Behavior<ControllerCommand> create(
    ActorRef<Gate.GateCommand> gate,
    ActorRef<LightMachine.LightMachineCommand> lightMachine
  ) {
    return Behaviors.setup(context -> new Controller(context, lightMachine, gate));
  }

  private Controller(
    ActorContext<ControllerCommand> context,
    ActorRef<LightMachine.LightMachineCommand> lightMachine,
    ActorRef<Gate.GateCommand> gate
  ) {
    super(context);
    this.lightMachine = lightMachine;
    this.gate = gate;
  }

  @Override
  public Receive<ControllerCommand> createReceive() {
    return newReceiveBuilder()
      .onMessage(CommandTrainSeen.class, msg -> onTrainSeen())
      .onMessage(CommandTrainNotSeen.class, msg -> onTrainNotSeen())
      .onMessage(CommandGetControllerName.class, msg -> sendControllerName(msg.replyTo))
      .build();
  }

  private Behavior<ControllerCommand> onTrainSeen() {
    switch (state) {
      case Away -> {
        lightMachine.tell(new LightMachine.CommandTurnOn());
        gate.tell(new Gate.GateCommandClose());
        state = State.Approaching;
        logState(getContext(), state);
      }
      case Close -> {
        state = State.Present;
        logState(getContext(), state);
      }
      case Leaving -> {
        state = State.Left;
        logState(getContext(), state);
      }
    }
    return Behaviors.same();
  }

  private Behavior<ControllerCommand> onTrainNotSeen() {
    switch (state) {
      case Approaching -> {
        state = State.Close;
        logState(getContext(), state);
      }
      case Present -> {
        lightMachine.tell(new LightMachine.CommandTurnOff());
        gate.tell(new Gate.GateCommandOpen());
        state = State.Leaving;
        logState(getContext(), state);
      }
      case Left -> {
        state = State.Away;
        logState(getContext(), state);
      }
    }
    return Behaviors.same();
  }

  private Behavior<ControllerCommand> sendControllerName(
    ActorRef<SignalReceiver.SignalReceiverCommand> receiver
  ) {
    receiver.tell(
      new SignalReceiver.ControllerReply(
        getContext().getSelf().path().name(),
        getContext().getSelf()
      )
    );
    return Behaviors.same();
  }
}
