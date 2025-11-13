package actors.controller;

import actors.Command;
import actors.StateMachine;
import actors.api.SignalReceiver;
import actors.gate.Gate;
import actors.light_machine.LightMachine;
import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import akka.persistence.typed.javadsl.*;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class Controller
  extends AbstractBehavior<Controller.ControllerCommand>
  implements StateMachine<Controller.State> {

  public enum State {
    Away,
    Approaching,
    Close,
    Present,
    Leaving,
    Left
  }

  public interface ControllerCommand extends Command {}

  public static class CommandGetCrossingID implements ControllerCommand {

    public final ActorRef<SignalReceiver.SignalReceiverCommand> replyTo;

    @JsonCreator
    public CommandGetCrossingID(
      @JsonProperty("replyTo") ActorRef<SignalReceiver.SignalReceiverCommand> replyTo
    ) {
      this.replyTo = replyTo;
    }
  }

  public static class CommandTrainSeen implements ControllerCommand {}

  public static class CommandTrainNotSeen implements ControllerCommand {}

  private final ActorRef<LightMachine.LightMachineCommand> lightMachine;

  private final ActorRef<Gate.GateCommand> gate;

  private State state = State.Away;

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
        state = State.Present;
        logState(getContext(), state);
      }
      case Left -> {
        state = State.Away;
        logState(getContext(), state);
      }
    }
    return Behaviors.same();
  }

  private void sendControllerId(ActorRef<SignalReceiver.SignalReceiverCommand> receiver) {
    receiver.tell(
      new SignalReceiver.ControllerReply(
        getContext().getSelf().path().name(),
        getContext().getSelf()
      )
    );
  }
}
