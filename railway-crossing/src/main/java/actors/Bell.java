package actors;

import actors.common.Command;
import actors.services.RailwayService;
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
      .onMessage(CommandBellOff.class, cmd -> Behaviors.same())
      .onMessage(CommandBellOn.class, cmd -> {
        railwayService.bellOn(
          getContext(),
          getContext().getSelf().path().name(),
          cmd
        );
        return on();
      })
      .build();
  }

  /** Represents the Off-State of the Bell */
  private Behavior<BellCommand> off() {
    //logState(getContext(), State.Off);
    return createReceive();
  }

  /** Represents the On-State of the Bell */
  private Behavior<BellCommand> on() {
    //logState(getContext(), State.On);
    return newReceiveBuilder()
      .onMessage(CommandBellOff.class, cmd -> {
        railwayService.bellOff(
          getContext(),
          getContext().getSelf().path().name(),
          cmd
        );
        return off();
      })
      .onMessage(CommandBellOn.class, cmd -> Behaviors.same())
      .build();
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
  public record CommandBellOn(Double trainSpeed, String traceId, String spanId) implements BellCommand {
    @JsonCreator
    public CommandBellOn(
            @JsonProperty("trainSpeed") Double trainSpeed,
            @JsonProperty("traceId") String traceId,
            @JsonProperty("spanId") String spanId) {
      this.trainSpeed = trainSpeed;
      this.traceId = traceId;
      this.spanId = spanId;
    }
  }

  /**
   * Message to change the Bell state to {@link State#Off}.
   */
  public record CommandBellOff(String traceId, String spanId) implements
    BellCommand {
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
