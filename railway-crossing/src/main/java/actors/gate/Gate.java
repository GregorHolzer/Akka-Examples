package actors.gate;

import actors.Command;
import actors.StateMachine;
import actors.bell.Bell;
import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import service.RailwayService;

public class Gate extends AbstractBehavior<Gate.GateCommand> implements StateMachine<Gate.State> {

  public enum State {
    Open,
    Closed
  }

  public interface GateCommand extends Command {}

  public static class GateCommandOpen implements GateCommand {}

  public static class GateCommandClose implements GateCommand {}

  private final ActorRef<Bell.BellCommand> bell;

  private final RailwayService railwayService;

  private State state = State.Open;

  public static Behavior<GateCommand> create(
    ActorRef<Bell.BellCommand> bell,
    RailwayService railwayService
  ) {
    return Behaviors.setup(context -> new Gate(context, bell, railwayService));
  }

  private Gate(
    ActorContext<GateCommand> context,
    ActorRef<Bell.BellCommand> bell,
    RailwayService railwayService
  ) {
    super(context);
    this.bell = bell;
    this.railwayService = railwayService;
  }

  public Receive<GateCommand> createReceive() {
    return newReceiveBuilder()
      .onMessage(GateCommandOpen.class, cmd -> onGateOpen())
      .onMessage(GateCommandClose.class, cmd -> onGateClose())
      .build();
  }

  private Behavior<GateCommand> onGateClose() {
    if (state == State.Open) {
      bell.tell(new Bell.CommandBellOn());
      state = State.Closed;
      railwayService.gateDown(getContext(), getContext().getSelf().path().name());
      logState(getContext(), state);
    }
    return Behaviors.same();
  }

  private Behavior<GateCommand> onGateOpen() {
    if (state == State.Closed) {
      bell.tell(new Bell.CommandBellOff());
      state = State.Open;
      railwayService.gateUp(getContext(), getContext().getSelf().path().name());
      logState(getContext(), state);
    }
    return Behaviors.same();
  }
}
