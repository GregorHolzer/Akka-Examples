package actors.bell;

import actors.Command;
import actors.StateMachine;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import service.RailwayService;

public class Bell extends AbstractBehavior<Bell.BellCommand> implements StateMachine<Bell.State> {

  /**
   * Defines States of the {@link Bell} actor
   */
  public enum State {
    On,
    Off
  }

  /**
   * Defines the message-type {@link Bell} can receive
   */
  public interface BellCommand extends Command {}

  /**
   * Message that changes the {@link State} to {@link State#On}
   */
  public static class CommandBellOn implements BellCommand {}

  /**
   * Message that changes the {@link State} to {@link State#Off}
   */
  public static class CommandBellOff implements BellCommand {}

  private final RailwayService railwayService;

  private State state = State.Off;

  /**
   * Creates a new {@link Bell} actor.
   *
   * @param railwayService service that is invoked upon messages @see {@link RailwayService}
   * @return a new {@link Behavior} instance for the {@link Bell} actor
   */
  public static Behavior<BellCommand> create(RailwayService railwayService) {
    return Behaviors.setup(context -> new Bell(context, railwayService));
  }

  private Bell(ActorContext<Bell.BellCommand> context, RailwayService railwayService) {
    super(context);
    this.railwayService = railwayService;
  }

  /**
   * Defines how to handle {@link BellCommand}s
   * @return the initial {@link Behavior} for the {@link Bell} actor
   */
  @Override
  public Receive<BellCommand> createReceive() {
    return newReceiveBuilder()
      .onMessage(CommandBellOn.class, msg -> onTurnOn())
      .onMessage(CommandBellOff.class, msg -> onTurnOff())
      .build();
  }

  /**
   * Updates the {@link State} and invokes service {@link RailwayService#bellOn(ActorContext, String)}
   * @return the same {@link Behavior} as before
   */
  private Behavior<Bell.BellCommand> onTurnOn() {
    if (state == State.Off) {
      state = State.On;
      railwayService.bellOn(getContext(), getContext().getSelf().path().name());
      logState(getContext(), Bell.State.On);
    }
    return Behaviors.same();
  }

  /**
   * Updates the {@link State} and invokes service {@link RailwayService#bellOff(ActorContext, String)}
   * @return the same {@link Behavior} as before
   */
  private Behavior<Bell.BellCommand> onTurnOff() {
    if (state == State.On) {
      state = State.Off;
      railwayService.bellOff(getContext(), getContext().getSelf().path().name());
      logState(getContext(), state);
    }
    return Behaviors.same();
  }
}
