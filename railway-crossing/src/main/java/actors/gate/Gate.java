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
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import service.RailwayService;

public class Gate extends AbstractBehavior<Gate.GateCommand> implements StateMachine<Gate.State> {

  public enum State {
    Open,
    Closed
  }

  public interface GateCommand extends Command {}

  public static class CommandOpen implements GateCommand {}

  public static class CommandClose implements GateCommand {
      public Double trainSpeed;

      @JsonCreator
      public CommandClose(@JsonProperty("trainSpeed") Double trainSpeed) {
          this.trainSpeed = trainSpeed;
      }
  }

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
      .onMessage(CommandOpen.class, msg -> onGateOpen())
      .onMessage(CommandClose.class, msg -> onGateClose(msg.trainSpeed))
      .build();
  }

  private Behavior<GateCommand> onGateClose(Double trainSpeed) {
    if (state == State.Open) {
      bell.tell(new Bell.CommandBellOn(trainSpeed));
      state = State.Closed;
      railwayService.gateDown(getContext(), getContext().getSelf().path().name(), trainSpeed);
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
