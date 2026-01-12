package actors;

import actors.common.Command;
import actors.common.RailwayService;
import actors.common.StateMachine;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * LightMachine Actor:
 * <p>
 * Manages the warning lights of a railway crossing and receives messages from a {@link Controller}.
 * The actor represents a finite state machine with two states:
 * </p>
 * <ul>
 *   <li>{@link LightMachine.State#On}</li>
 *   <li>{@link LightMachine.State#Off}</li>
 * </ul>
 */
public class LightMachine
  extends AbstractBehavior<LightMachine.LightMachineCommand>
  implements StateMachine<LightMachine.State> {

  /** Service to turn the LightMachine on or off */
  private final RailwayService railwayService;

  private LightMachine(
    ActorContext<LightMachineCommand> context,
    RailwayService railwayService
  ) {
    super(context);
    this.railwayService = railwayService;
  }

  /**
   * Creates a new {@link LightMachine} Actor.
   *
   * @param railwayService the {@link RailwayService} used by the LightMachine Actor
   * @return the {@link Behavior} of the created {@link LightMachine} Actor
   */
  public static Behavior<LightMachineCommand> create(
    RailwayService railwayService
  ) {
    return Behaviors.setup(context ->
      new LightMachine(context, railwayService)
    );
  }

  /**
   * Defines the {@link Behavior} of the {@link LightMachine} Actor.
   * <p>
   * Handles messages from the {@link Controller}.
   * </p>
   */
  @Override
  public Receive<LightMachineCommand> createReceive() {
    return newReceiveBuilder()
      .onMessage(CommandTurnOff.class, cmd -> Behaviors.same())
      .onMessage(CommandTurnOn.class, cmd -> {
        railwayService.lightOn(
          getContext(),
          getContext().getSelf().path().name(),
          cmd.trainSpeed
        );
        return on();
      })
      .build();
  }

  /** Represents the Off-State of the LightMachine */
  private Behavior<LightMachineCommand> off() {
    logState(getContext(), State.Off);
    return createReceive();
  }

  /** Represents the On-State of the LightMachine */
  private Behavior<LightMachineCommand> on() {
    logState(getContext(), State.On);
    return newReceiveBuilder()
      .onMessage(CommandTurnOff.class, cmd -> {
        railwayService.lightOff(
          getContext(),
          getContext().getSelf().path().name()
        );
        return off();
      })
      .build();
  }

  /**
   * States of the LightMachine Actor
   */
  public enum State {
    On,
    Off,
  }

  /**
   * Marker interface for messages that the LightMachine Actor can receive
   */
  public interface LightMachineCommand extends Command {}

  /**
   * Message to change the LightMachine state to {@link State#On}.
   */
  public static class CommandTurnOn implements LightMachineCommand {

    public Double trainSpeed;

    @JsonCreator
    public CommandTurnOn(@JsonProperty("trainSpeed") Double trainSpeed) {
      this.trainSpeed = trainSpeed;
    }
  }

  /**
   * Message to change the LightMachine state to {@link State#Off}.
   */
  public static class CommandTurnOff implements LightMachineCommand {}
}
