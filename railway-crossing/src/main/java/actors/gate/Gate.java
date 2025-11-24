package actors.gate;

import actors.Command;
import actors.StateMachine;
import actors.bell.Bell;
import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import service.RailwayService;

public class Gate extends AbstractBehavior<Gate.GateCommand> implements StateMachine<Gate.State> {

  public enum State {
    Open,
    Closed
  }

  public interface GateCommand extends Command {}

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

  public static class CommandClose implements GateCommand {

    public Double trainSpeed;

    @JsonCreator
    public CommandClose(@JsonProperty("trainSpeed") Double trainSpeed) {
      this.trainSpeed = trainSpeed;
    }
  }

  public static class WrappedInvocationResponse implements GateCommand {

    public RailwayService.InvocationResponse response;

    @JsonCreator
    public WrappedInvocationResponse(
      @JsonProperty("result") RailwayService.InvocationResponse response
    ) {
      this.response = response;
    }
  }

  private final ActorRef<RailwayService.InvocationResponse> messageAdapter;

  private final ActorRef<Bell.BellCommand> bell;

  private final RailwayService railwayService;

  private State state = State.Open;

  private String currentTraceId;

  private String currentSpanId;

  public static Behavior<GateCommand> create(
    ActorRef<Bell.BellCommand> bell,
    RailwayService railwayService
  ) {
    return Behaviors.setup(context -> new Gate(context, bell, railwayService));
  }

  private Gate(
    ActorContext<GateCommand> context,
    ActorRef<Bell.BellCommand> bell,
    RailwayService railwayService
  ) {
    super(context);
    this.bell = bell;
    this.railwayService = railwayService;
    this.messageAdapter = context.messageAdapter(
      RailwayService.InvocationResponse.class,
      WrappedInvocationResponse::new
    );
  }

  public Receive<GateCommand> createReceive() {
    return newReceiveBuilder()
      .onMessage(CommandOpen.class, this::onGateOpen)
      .onMessage(CommandClose.class, msg -> onGateClose(msg.trainSpeed))
      .onMessage(WrappedInvocationResponse.class, this::onWrappedInvocationResponse)
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
      currentTraceId = cmd.traceId;
      currentSpanId = cmd.spanId;
      railwayService.gateUp(getContext(), messageAdapter, getContext().getSelf().path().name());
      state = State.Open;
      logState(getContext(), state);
    }
    return Behaviors.same();
  }

  private Behavior<GateCommand> onWrappedInvocationResponse(
    WrappedInvocationResponse wrappedInvocationResponse
  ) {
    RailwayService.InvocationResult result = wrappedInvocationResponse.response.result;
    if (result == RailwayService.InvocationResult.Success) {
      bell.tell(new Bell.CommandBellOff(currentTraceId, currentSpanId));
      getContext().getLog().info("Gate service invocation was successful");
    } else {
      getContext().getLog().error("Gate service invocation failed");
    }
    return Behaviors.same();
  }
}
