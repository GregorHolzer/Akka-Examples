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
  public static class CommandBellOn implements BellCommand {

    public Double trainSpeed;

    @JsonCreator
    public CommandBellOn(@JsonProperty("trainSpeed") Double trainSpeed) {
      this.trainSpeed = trainSpeed;
    }
  }

  /**
   * Message that changes the {@link State} to {@link State#Off}
   */
  public static class CommandBellOff implements BellCommand {

    public String traceId;

    public String spanId;

    @JsonCreator
    public CommandBellOff(
      @JsonProperty("traceId") String traceId,
      @JsonProperty("spanId") String spanId
    ) {
      this.traceId = traceId;
      this.spanId = spanId;
    }
  }

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
      .onMessage(CommandBellOn.class, msg -> onTurnOn(msg.trainSpeed))
      .onMessage(CommandBellOff.class, this::onTurnOff)
      .build();
  }

  /**
   * Updates the {@link State} and invokes service {@link RailwayService#bellOn(ActorContext, String, Double)}
   * @return the same {@link Behavior} as before
   */
  private Behavior<Bell.BellCommand> onTurnOn(Double trainSpeed) {
    if (state == State.Off) {
      state = State.On;
      railwayService.bellOn(getContext(), getContext().getSelf().path().name(), trainSpeed);
      logState(getContext(), Bell.State.On);
    }
    return Behaviors.same();
  }

  /**
   * Updates the {@link State} and invokes service {@link RailwayService#bellOff(ActorContext, String, String, String)}
   * @return the same {@link Behavior} as before
   */
  private Behavior<Bell.BellCommand> onTurnOff(CommandBellOff cmd) {
    if (state == State.On) {
      /*Span span = TelemetryJaeger.createNewSpan(cmd.traceId, cmd.spanId, "bell", "off");
        try{
            span.makeCurrent();
            state = State.Off;
            railwayService.bellOff(
                    getContext(),
                    getContext().getSelf().path().name(),
                    span.getSpanContext().getTraceId(),
                    span.getSpanContext().getSpanId()
            );
            logState(getContext(), state);
        }
        finally {
            span.end();
        }*/
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
}
