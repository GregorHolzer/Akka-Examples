package actors;

import actors.common.Command;
import actors.common.StateMachine;
import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import actors.common.RailwayService;

/**
 * Gate Actor: Manages the Gate of a Railway-Crossing, Receives Messages from a {@link Controller}, Sends Messages to a {@link Bell}
 * The actor represents a finite state machine with two states:
 * - {@link Gate.State#Open}
 * - {@link Gate.State#Closed}
 */
public class Gate extends AbstractBehavior<Gate.GateCommand> implements StateMachine<Gate.State> {

  private final ActorRef<Bell.BellCommand> bell;

  private final RailwayService railwayService;

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

  public static Behavior<GateCommand> create(
    ActorRef<Bell.BellCommand> bell,
    RailwayService railwayService
  ) {
    return Behaviors.setup(context -> new Gate(context, bell, railwayService));
  }

  public Receive<GateCommand> createReceive() {
    return newReceiveBuilder()
      .onMessage(CommandOpen.class, this::onGateOpen)
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

  private Behavior<GateCommand> onGateOpen(CommandOpen cmd) {
    if (state == State.Closed) {
      /*Span span = Telemetry.createNewSpan(cmd.traceId, cmd.spanId, "gate", "gate-open");
        try{
            span.makeCurrent();
            railwayService.gateUp(getContext(), bell, getContext().getSelf().path().name(), span.getSpanContext().getTraceId(), span.getSpanContext().getSpanId());
            state = State.Open;
            logState(getContext(), state);
            timesGateOpened++;
            getContext().getLog().info("Number gate was opend: {}", timesGateOpened);
        }
        finally {
            span.end();
        }*/
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
   * Defines States of the Gate
   */
  public enum State {
    Open,
    Closed
  }

  /**
   * Marker interface for commands accepted by Gate.
   */
  public interface GateCommand extends Command {}

  /**
   * Command to open the Gate.
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
   * Command to close the Gate.
   */
  public static class CommandClose implements GateCommand {

    public Double trainSpeed;

    @JsonCreator
    public CommandClose(@JsonProperty("trainSpeed") Double trainSpeed) {
      this.trainSpeed = trainSpeed;
    }
  }
}
