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

/**
 * Controller Actor:
 * <p>
 * Handles the sensor events of a railway crossing and sends messages to a {@link LightMachine} and a {@link Gate}.
 * The actor represents a finite state machine with six states:
 * </p>
 * <ul>
 *   <li>{@link Controller.State#Away}</li>
 *   <li>{@link Controller.State#Approaching}</li>
 *   <li>{@link Controller.State#Close}</li>
 *   <li>{@link Controller.State#Present}</li>
 *   <li>{@link Controller.State#Leaving}</li>
 *   <li>{@link Controller.State#Left}</li>
 * </ul>
 */
public class Controller
  extends AbstractBehavior<Controller.ControllerCommand>
  implements StateMachine<Controller.State> {

  /** Reference to the LightMachine actor */
  private final ActorRef<LightMachine.LightMachineCommand> lightMachine;

  /** Reference to the Gate actor */
  private final ActorRef<Gate.GateCommand> gate;

  private Controller(
    ActorContext<ControllerCommand> context,
    ActorRef<LightMachine.LightMachineCommand> lightMachine,
    ActorRef<Gate.GateCommand> gate
  ) {
    super(context);
    this.lightMachine = lightMachine;
    this.gate = gate;
  }

  /**
   * Creates a new {@link Controller} Actor.
   *
   * @param gate the {@link Gate} actor reference
   * @param lightMachine the {@link LightMachine} actor reference
   * @return the {@link Behavior} of the created {@link Controller} Actor
   */
  public static Behavior<ControllerCommand> create(
    ActorRef<Gate.GateCommand> gate,
    ActorRef<LightMachine.LightMachineCommand> lightMachine
  ) {
    return Behaviors.setup(context ->
      new Controller(context, lightMachine, gate)
    );
  }

  /**
   * Defines the {@link Behavior} of the {@link Controller} Actor.
   * <p>
   * Handles sensor messages of the railway crossing
   * </p>
   */
  @Override
  public Receive<ControllerCommand> createReceive() {
    return newReceiveBuilder()
      .onMessage(CommandTrainNotSeen.class, cmd -> Behaviors.same())
      .onMessage(CommandTrainSeen.class, cmd -> approaching())
      .build();
  }

  /** Represents the Away-State of the Controller */
  private Behavior<ControllerCommand> away() {
    logState(getContext(), State.Away);
    return createReceive();
  }

  /** Represents the Approaching-State of the Controller */
  private Behavior<ControllerCommand> approaching() {
    logState(getContext(), State.Approaching);
    return newReceiveBuilder()
      .onMessage(CommandTrainNotSeen.class, cmd -> {
        lightMachine.tell(new LightMachine.CommandTurnOn(cmd.trainSpeed));
        gate.tell(new Gate.CommandClose(cmd.trainSpeed));
        return close();
      })
      .onMessage(CommandTrainSeen.class, cmd -> Behaviors.same())
      .build();
  }

  /** Represents the Close-State of the Controller */
  private Behavior<ControllerCommand> close() {
    logState(getContext(), State.Close);
    return newReceiveBuilder()
      .onMessage(CommandTrainNotSeen.class, cmd -> Behaviors.same())
      .onMessage(CommandTrainSeen.class, cmd -> present())
      .build();
  }

  /** Represents the Present-State of the Controller */
  private Behavior<ControllerCommand> present() {
    logState(getContext(), State.Present);
    return newReceiveBuilder()
      .onMessage(CommandTrainNotSeen.class, cmd -> {
        lightMachine.tell(new LightMachine.CommandTurnOff());
        gate.tell(new Gate.CommandOpen(cmd.traceId, cmd.spanId));
        return leaving();
      })
      .onMessage(CommandTrainSeen.class, cmd -> Behaviors.same())
      .build();
  }

  /** Represents the Leaving-State of the Controller */
  private Behavior<ControllerCommand> leaving() {
    logState(getContext(), State.Leaving);
    return newReceiveBuilder()
      .onMessage(CommandTrainNotSeen.class, cmd -> Behaviors.same())
      .onMessage(CommandTrainSeen.class, cmd -> left())
      .build();
  }

  /** Represents the Left-State of the Controller */
  private Behavior<ControllerCommand> left() {
    logState(getContext(), State.Left);
    return newReceiveBuilder()
      .onMessage(CommandTrainNotSeen.class, cmd -> away())
      .onMessage(CommandTrainSeen.class, cmd -> Behaviors.same())
      .build();
  }

  /**
   * States of the Controller Actor
   */
  public enum State {
    Away,
    Approaching,
    Close,
    Present,
    Leaving,
    Left,
  }

  /**
   * Marker interface for messages that the Controller Actor can receive
   */
  public interface ControllerCommand extends Command {}

  /**
   * Abstract base class for sensor event messages.
   */
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
   * Message indicating that a sensor detects a train.
   */
  public static class CommandTrainSeen extends SensorCommand {

    @JsonCreator
    public CommandTrainSeen(Double trainSpeed, String traceId, String spanId) {
      super(trainSpeed, traceId, spanId);
    }
  }

  /**
   * Message indicating that a sensor no longer detects a train.
   */
  public static class CommandTrainNotSeen extends SensorCommand {

    @JsonCreator
    public CommandTrainNotSeen(
      Double trainSpeed,
      String traceId,
      String spanId
    ) {
      super(trainSpeed, traceId, spanId);
    }
  }
}
