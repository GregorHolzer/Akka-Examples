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
 * Bell Actor:
 * <p>
 * Represents the Bell Component of a Railway-Crossing and implements a finite state machine with two states:
 * </p>
 * <ul>
 *   <li>{@link Bell.State#On}</li>
 *   <li>{@link Bell.State#Off}</li>
 * </ul>
 * </p>
 */
public class Bell
  extends AbstractBehavior<Bell.BellCommand>
  implements StateMachine<Bell.State> {

  /** Service to turn the Bell on or to turn the Bell off */
  private final RailwayService railwayService;

  /** Current state of the Bell: initial Off */
  private State state = State.Off;

  private Bell(
    ActorContext<Bell.BellCommand> context,
    RailwayService railwayService
  ) {
    super(context);
    this.railwayService = railwayService;
  }

  /**
   * Creates a new {@link Bell} Actor.
   *
   * @param railwayService the {@link RailwayService} used by the Bell Actor
   * @return the {@link Behavior} of the created {@link Bell} Actor
   */
  public static Behavior<BellCommand> create(RailwayService railwayService) {
    return Behaviors.setup(context -> new Bell(context, railwayService));
  }

  /**
   * Defines the {@link Behavior} of the {@link Bell} Actor.
   * <p>
   * Handles messages from the {@link Gate}.
   * </p>
   */
  @Override
  public Receive<BellCommand> createReceive() {
    return newReceiveBuilder()
      .onMessage(CommandBellOn.class, msg -> onTurnOn(msg.trainSpeed))
      .onMessage(CommandBellOff.class, this::onTurnOff)
      .build();
  }

  /**
   * Handles the {@link CommandBellOn} message and turns the bell on.
   *
   * @param trainSpeed the speed of the approaching train
   */
  private Behavior<Bell.BellCommand> onTurnOn(Double trainSpeed) {
    if (state == State.Off) {
      state = State.On;
      railwayService.bellOn(
        getContext(),
        getContext().getSelf().path().name(),
        trainSpeed
      );
      logState(getContext(), Bell.State.On);
    }
    return Behaviors.same();
  }

  /**
   * Handles the {@link CommandBellOff} message and turns the bell off.
   *
   * @param cmd message from the {@link Gate} containing tracing information
   */
  private Behavior<Bell.BellCommand> onTurnOff(CommandBellOff cmd) {
    if (state == State.On) {
      state = State.Off;
      railwayService.bellOff(
        getContext(),
        getContext().getSelf().path().name(),
        cmd.traceId,
        cmd.spanId
      );
      logState(getContext(), state);
    }
    return Behaviors.same();
  }

  /**
   * States of the Bell Actor
   */
  public enum State {
    On,
    Off,
  }

  /**
   * Marker interface for messages that the Bell Actor can receive
   */
  public interface BellCommand extends Command {}

  /**
   * Message to change the Bell state to {@link State#On}.
   */
  public static class CommandBellOn implements BellCommand {

    public final Double trainSpeed;

    @JsonCreator
    public CommandBellOn(@JsonProperty("trainSpeed") Double trainSpeed) {
      this.trainSpeed = trainSpeed;
    }
  }

  /**
   * Message to change the Bell state to {@link State#Off}.
   */
  public static class CommandBellOff implements BellCommand {

    public final String traceId;

    public final String spanId;

    @JsonCreator
    public CommandBellOff(
      @JsonProperty("traceId") String traceId,
      @JsonProperty("spanId") String spanId
    ) {
      this.traceId = traceId;
      this.spanId = spanId;
    }
  }
}
