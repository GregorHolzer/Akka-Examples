package actors;

import actors.common.Command;
import actors.common.RailwayService;
import actors.common.StateMachine;
import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Gate Actor:
 * <p>
 * Manages the gate of a railway crossing, receives messages from a {@link Controller}, and sends messages to a {@link Bell}.
 * The actor represents a finite state machine with two states:
 * </p>
 * <ul>
 *   <li>{@link Gate.State#Open}</li>
 *   <li>{@link Gate.State#Closed}</li>
 * </ul>
 */
public class Gate
  extends AbstractBehavior<Gate.GateCommand>
  implements StateMachine<Gate.State> {

  /** Reference to the Bell actor */
  private final ActorRef<Bell.BellCommand> bell;

  /** Service to open or close the Gate */
  private final RailwayService railwayService;

  /** Current state of the Gate: initial Open */
  private State state = State.Open;

  private Gate(
    ActorContext<GateCommand> context,
    ActorRef<Bell.BellCommand> bell,
    RailwayService railwayService
  ) {
    super(context);
    this.bell = bell;
    this.railwayService = railwayService;
  }

  /**
   * Creates a new {@link Gate} Actor.
   *
   * @param bell the {@link Bell} actor reference
   * @param railwayService the {@link RailwayService} used by the Gate Actor
   * @return the {@link Behavior} of the created {@link Gate} Actor
   */
  public static Behavior<GateCommand> create(
    ActorRef<Bell.BellCommand> bell,
    RailwayService railwayService
  ) {
    return Behaviors.setup(context -> new Gate(context, bell, railwayService));
  }

  /**
   * Defines the {@link Behavior} of the {@link Gate} Actor.
   * <p>
   * Handles messages from the {@link Controller}.
   * </p>
   */
  public Receive<GateCommand> createReceive() {
    return newReceiveBuilder()
      .onMessage(CommandOpen.class, this::onGateOpen)
      .onMessage(CommandClose.class, msg -> onGateClose(msg.trainSpeed))
      .build();
  }

  /**
   * Handles the {@link CommandClose} message and closes the gate.
   *
   * @param trainSpeed the speed of the approaching train
   */
  private Behavior<GateCommand> onGateClose(Double trainSpeed) {
    if (state == State.Open) {
      bell.tell(new Bell.CommandBellOn(trainSpeed));
      state = State.Closed;
      railwayService.gateDown(
        getContext(),
        getContext().getSelf().path().name(),
        trainSpeed
      );
      logState(getContext(), state);
    }
    return Behaviors.same();
  }

  /**
   * Handles the {@link CommandOpen} message and opens the gate.
   *
   * @param cmd message from the {@link Controller} containing tracing information
   */
  private Behavior<GateCommand> onGateOpen(CommandOpen cmd) {
    if (state == State.Closed) {
      railwayService.gateUp(
        getContext(),
        bell,
        getContext().getSelf().path().name(),
        cmd.traceId,
        cmd.spanId
      );
      state = State.Open;
      logState(getContext(), state);
    }
    return Behaviors.same();
  }

  /**
   * States of the Gate Actor
   */
  public enum State {
    Open,
    Closed,
  }

  /**
   * Marker interface for messages that the Gate Actor can receive
   */
  public interface GateCommand extends Command {}

  /**
   * Message to change the Gate state to {@link State#Open}.
   */
  public static class CommandOpen implements GateCommand {

    public String traceId;

    public String spanId;

    @JsonCreator
    public CommandOpen(
      @JsonProperty("traceId") String traceId,
      @JsonProperty("spanId") String spanId
    ) {
      this.traceId = traceId;
      this.spanId = spanId;
    }
  }

  /**
   * Message to change the Gate state to {@link State#Closed}.
   */
  public static class CommandClose implements GateCommand {

    public Double trainSpeed;

    @JsonCreator
    public CommandClose(@JsonProperty("trainSpeed") Double trainSpeed) {
      this.trainSpeed = trainSpeed;
    }
  }
}
