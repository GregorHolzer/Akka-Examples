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
      .onMessage(CommandOpen.class, cmd -> Behaviors.same())
      .onMessage(CommandClose.class, cmd -> {
        railwayService.gateDown(
          getContext(),
          getContext().getSelf().path().name(),
          cmd.trainSpeed
        );
        bell.tell(new Bell.CommandBellOn(cmd.trainSpeed));
        return closed();
      })
      .build();
  }

  /** Represents the Open-State of the Gate Actor */
  private Behavior<GateCommand> open() {
    logState(getContext(), State.Open);
    return createReceive();
  }

  /** Represents the Closed-State of the Gate Actor */
  private Behavior<GateCommand> closed() {
    logState(getContext(), State.Closed);
    return newReceiveBuilder()
      .onMessage(CommandOpen.class, cmd -> {
        railwayService.gateUp(
          getContext(),
          bell,
          getContext().getSelf().path().name(),
          cmd.traceId,
          cmd.spanId
        );
        return open();
      })
      .onMessage(CommandClose.class, cmd -> Behaviors.same())
      .build();
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
