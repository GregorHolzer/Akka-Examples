package actors.light_machine;

import actors.Command;
import actors.StateMachine;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import service.RailwayService;

public class LightMachine
  extends AbstractBehavior<LightMachine.LightMachineCommand>
  implements StateMachine<LightMachine.State> {

  public enum State {
    On,
    Off
  }

  public interface LightMachineCommand extends Command {}

  public static class CommandTurnOn implements LightMachineCommand {
      public Double trainSpeed;

      @JsonCreator
      public CommandTurnOn(@JsonProperty("trainSpeed") Double trainSpeed) {
          this.trainSpeed = trainSpeed;
      }
  }

  public static class CommandTurnOff implements LightMachineCommand {
      public Double trainSpeed;

      @JsonCreator
      public CommandTurnOff(@JsonProperty("trainSpeed") Double trainSpeed) {
          this.trainSpeed = trainSpeed;
      }
  }

  private final RailwayService railwayService;

  private State state = State.Off;

  public static Behavior<LightMachineCommand> create(RailwayService railwayService) {
    return Behaviors.setup(context -> new LightMachine(context, railwayService));
  }

  private LightMachine(ActorContext<LightMachineCommand> context, RailwayService railwayService) {
    super(context);
    this.railwayService = railwayService;
  }

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
}
