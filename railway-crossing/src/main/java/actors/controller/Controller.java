package actors.controller;

import actors.Command;
import actors.Event;
import actors.api.SignalReceiver;
import actors.gate.Gate;
import actors.light_machine.LightMachine;
import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.persistence.typed.PersistenceId;
import akka.persistence.typed.javadsl.*;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class Controller
  extends EventSourcedBehavior<
    Controller.ControllerCommand,
    Controller.ControllerEvent,
    ControllerState
  > {

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

  public interface ControllerEvent extends Event {}

  public static class EventAdvanceState implements ControllerEvent {}

  public static class EventRaiseApproaching implements ControllerEvent {}

  public static class EventRaiseLeaving implements ControllerEvent {}

  private final ActorContext<ControllerCommand> context;

  private final ActorRef<LightMachine.LightMachineCommand> lightMachine;

  private final ActorRef<Gate.GateCommand> gate;

  public static Behavior<ControllerCommand> create(
    PersistenceId persistenceId,
    ActorRef<Gate.GateCommand> gate,
    ActorRef<LightMachine.LightMachineCommand> lightMachine
  ) {
    return Behaviors.setup(context -> new Controller(persistenceId, context, lightMachine, gate));
  }

  private Controller(
    PersistenceId persistenceId,
    ActorContext<ControllerCommand> context,
    ActorRef<LightMachine.LightMachineCommand> lightMachine,
    ActorRef<Gate.GateCommand> gate
  ) {
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
    CommandHandlerBuilder<ControllerCommand, ControllerEvent, ControllerState> builder =
      newCommandHandlerBuilder();

    //Handle Away State
    builder
      .forState(state -> state.getState() == ControllerState.State.AWAY)
      .onCommand(CommandTrainSeen.class, cmd ->
        Effect().persist(List.of(new EventAdvanceState(), new EventRaiseApproaching()))
      );

    //Handle Present State
    builder
      .forState(state -> state.getState() == ControllerState.State.PRESENT)
      .onCommand(CommandTrainNotSeen.class, cmd ->
        Effect().persist(List.of(new EventAdvanceState(), new EventRaiseLeaving()))
      );

    //Handle even States (Close, Leaving)
    builder
      .forState(state -> state.getState().ordinal() % 2 == 0)
      .onCommand(CommandTrainSeen.class, cmd -> Effect().persist(new EventAdvanceState()));

    //Handle odd States (Approaching, Left)
    builder
      .forState(state -> state.getState().ordinal() % 2 != 0)
      .onCommand(CommandTrainNotSeen.class, cmd -> Effect().persist(new EventAdvanceState()));

    builder
      .forAnyState()
      .onCommand(CommandGetCrossingID.class, cmd ->
        Effect()
          .none()
          .thenRun(() -> sendControllerId(cmd.replyTo))
      )
      .build();

    builder.forAnyState().onAnyCommand(cmd -> Effect().none());

    return builder.build();
  }

  @Override
  public EventHandler<ControllerState, ControllerEvent> eventHandler() {
    return newEventHandlerBuilder()
      .forAnyState()
      .onEvent(EventAdvanceState.class, (state, event) -> {
        context.getLog().info("Advance state to {}", state.advanceState().getState());
        return (ControllerState) state.advanceState();
      })
      .onEvent(EventRaiseApproaching.class, (state, event) -> {
        lightMachine.tell(new LightMachine.CommandTurnOn());
        gate.tell(new Gate.GateCommandClose());
        context.getLog().info("Sent Approaching Command");
        return new ControllerState(state.getState());
      })
      .onEvent(EventRaiseLeaving.class, (state, event) -> {
        lightMachine.tell(new LightMachine.CommandTurnOff());
        gate.tell(new Gate.GateCommandOpen());
        context.getLog().info("Sent Leaving Command");
        return new ControllerState(state.getState());
      })
      .onEvent(ControllerEvent.class, (state, event) -> new ControllerState(state.getState()))
      .build();
  }

  private void sendControllerId(ActorRef<SignalReceiver.SignalReceiverCommand> receiver) {
    receiver.tell(
      new SignalReceiver.ControllerReply(context.getSelf().path().name(), context.getSelf())
    );
  }
}
