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

public class Controller
  extends AbstractBehavior<Controller.ControllerCommand>
  implements StateMachine<Controller.State> {

  /**
   * Defines States of the {@link Controller} actor
   */
  public enum State {
    Away,
    Approaching,
    Close,
    Present,
    Leaving,
    Left
  }

  /**
   * Defines the message-type {@link Controller} can receive
   */
  public interface ControllerCommand extends Command {}

  public abstract static class SensorCommand implements ControllerCommand {

    public Double trainSpeed;

    public String traceId;

    public String spanId;

    @JsonCreator
    public SensorCommand(
      @JsonProperty("trainSpeed") Double trainSpeed,
      @JsonProperty("traceId") String traceId,
      @JsonProperty("spanId") String spanId
    ) {
      this.trainSpeed = trainSpeed;
      this.traceId = traceId;
      this.spanId = spanId;
    }
  }

  /**
   * Message that indicates that a sensor detects a train
   */
  public static class CommandSensorSeen extends SensorCommand {

    @JsonCreator
    public CommandSensorSeen(Double trainSpeed, String traceId, String spanId) {
      super(trainSpeed, traceId, spanId);
    }
  }

  /**
   * Message that indicates that a sensor no longer detects a train
   */
  public static class CommandSensorNotSeen extends SensorCommand {

    @JsonCreator
    public CommandSensorNotSeen(Double trainSpeed, String traceId, String spanId) {
      super(trainSpeed, traceId, spanId);
    }
  }

  private final ActorRef<LightMachine.LightMachineCommand> lightMachine;

  private final ActorRef<Gate.GateCommand> gate;

  private State state = State.Away;

  /**
   * Creates a new {@link Controller} actor.
   *
   * @param gate {@link ActorRef} of the {@link Gate} actor that belongs to the same crossing
   * @param lightMachine {@link ActorRef} of the {@link LightMachine} actor that belongs to the same crossing
   * @return a new {@link Behavior} instance for the {@link Bell} actor
   */
  public static Behavior<ControllerCommand> create(
    ActorRef<Gate.GateCommand> gate,
    ActorRef<LightMachine.LightMachineCommand> lightMachine
  ) {
    return Behaviors.setup(context -> new Controller(context, lightMachine, gate));
  }

  private Controller(
    ActorContext<ControllerCommand> context,
    ActorRef<LightMachine.LightMachineCommand> lightMachine,
    ActorRef<Gate.GateCommand> gate
  ) {
    super(context);
    this.lightMachine = lightMachine;
    this.gate = gate;
  }

  @Override
  public Receive<ControllerCommand> createReceive() {
    return newReceiveBuilder()
      .onMessage(CommandSensorSeen.class, cmd -> onTrainSeen())
      .onMessage(CommandSensorNotSeen.class, this::onTrainNotSeen)
      .build();
  }

  private Behavior<ControllerCommand> onTrainSeen() {
    switch (state) {
      case Away -> {
        state = State.Approaching;
        logState(getContext(), state);
      }
      case Close -> {
        state = State.Present;
        logState(getContext(), state);
      }
      case Leaving -> {
        state = State.Left;
        logState(getContext(), state);
      }
    }
    return Behaviors.same();
  }

  private Behavior<ControllerCommand> onTrainNotSeen(CommandSensorNotSeen cmd) {
    switch (state) {
      case Approaching -> {
        getContext().getLog().info("Passed on TrainSpeed: {}", cmd.trainSpeed);
        lightMachine.tell(new LightMachine.CommandTurnOn(cmd.trainSpeed));
        gate.tell(new Gate.CommandClose(cmd.trainSpeed));
        state = State.Close;
        logState(getContext(), state);
      }
      case Present -> {
        /*
          Span span = Telemetry.createNewSpan(cmd.traceId, cmd.spanId, "controller", "train-leaving");
          try {
              span.makeCurrent();
              lightMachine.tell(new LightMachine.CommandTurnOff());
              gate.tell(new Gate.CommandOpen(span.getSpanContext().getTraceId(), span.getSpanContext().getSpanId()));
              state = State.Leaving;
              numberOfLeaving++;
              getContext().getLog().info("Number of Leaving: {}", numberOfLeaving);
              //logState(getContext(), state);
          } finally {
              span.end();
          }*/
        lightMachine.tell(new LightMachine.CommandTurnOff());
        gate.tell(new Gate.CommandOpen(cmd.traceId, cmd.spanId));
        state = State.Leaving;
        logState(getContext(), state);
      }
      case Left -> {
        state = State.Away;
        logState(getContext(), state);
      }
    }
    return Behaviors.same();
  }
}
