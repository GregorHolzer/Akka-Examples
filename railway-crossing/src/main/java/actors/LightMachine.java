package actors;

import actors.common.Command;
import actors.common.StateMachine;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import actors.common.RailwayService;

/**
 * LightMachine Actor: Manages the Light of a Railway-Crossing, Receives Messages from a {@link Controller}
 * The actor represents a finite state machine with two states:
 * - {@link LightMachine.State#On}
 * - {@link LightMachine.State#Off}
 */
public class LightMachine
        extends AbstractBehavior<LightMachine.LightMachineCommand>
        implements StateMachine<LightMachine.State> {

  private final RailwayService railwayService;

  private State state = State.Off;

  private LightMachine(ActorContext<LightMachineCommand> context, RailwayService railwayService) {
    super(context);
    this.railwayService = railwayService;
  }

  public static Behavior<LightMachineCommand> create(RailwayService railwayService) {
    return Behaviors.setup(context -> new LightMachine(context, railwayService));
  }

  @Override
  public Receive<LightMachineCommand> createReceive() {
    return newReceiveBuilder()
            .onMessage(CommandTurnOn.class, msg -> onTurnOn(msg.trainSpeed))
            .onMessage(CommandTurnOff.class, msg -> onTurnOff())
            .build();
  }

  private Behavior<LightMachineCommand> onTurnOn(Double trainSpeed) {
    if (state == State.Off) {
      state = State.On;
      railwayService.lightEarlyWarning(getContext(), getContext().getSelf().path().name());
      railwayService.lightOn(getContext(), getContext().getSelf().path().name(), trainSpeed);
      logState(getContext(), state);
    }
    return Behaviors.same();
  }

  private Behavior<LightMachineCommand> onTurnOff() {
    if (state == State.On) {
      state = State.Off;
      railwayService.lightOff(getContext(), getContext().getSelf().path().name());
      logState(getContext(), state);
    }
    return Behaviors.same();
  }

  /**
   * Represents the state of the light machine.
   */
  public enum State {
    On,
    Off
  }

  /**
   * Marker interface for commands accepted by LightMachine.
   */
  public interface LightMachineCommand extends Command {}

  /**
   * Command to turn on the light.
   */
  public static class CommandTurnOn implements LightMachineCommand {

    public Double trainSpeed;

    @JsonCreator
    public CommandTurnOn(@JsonProperty("trainSpeed") Double trainSpeed) {
      this.trainSpeed = trainSpeed;
    }
  }

  public static class CommandTurnOff implements LightMachineCommand {}
}
