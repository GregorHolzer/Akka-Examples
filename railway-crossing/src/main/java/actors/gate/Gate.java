package actors.gate;

import actors.Command;
import actors.Event;
import actors.bell.Bell;
import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.receptionist.Receptionist;
import akka.persistence.typed.PersistenceId;
import akka.persistence.typed.javadsl.CommandHandler;
import akka.persistence.typed.javadsl.CommandHandlerBuilder;
import akka.persistence.typed.javadsl.EventHandler;
import akka.persistence.typed.javadsl.EventSourcedBehavior;
import java.util.List;
import service.RailwayService;

public class Gate extends EventSourcedBehavior<Gate.GateCommand, Gate.GateEvent, GateState> {

  public interface GateCommand extends Command {}

  public static class GateCommandOpen implements GateCommand {}

  public static class GateCommandClose implements GateCommand {}

  public interface GateEvent extends Event {}

  public static class GateEventOpened implements GateEvent {}

  public static class GateEventClosed implements GateEvent {}

  public static class GateEventAdvanceState implements GateEvent {}

  private final ActorContext<GateCommand> context;

  private final ActorRef<Bell.BellCommand> bell;

  private final RailwayService railwayService;

  public static Behavior<GateCommand> create(
    PersistenceId persistenceId,
    ActorRef<Bell.BellCommand> bell,
    RailwayService railwayService
  ) {
    return Behaviors.setup(context -> {
      return new Gate(persistenceId, context, bell, railwayService);
    });
  }

  private Gate(
    PersistenceId persistenceId,
    ActorContext<GateCommand> context,
    ActorRef<Bell.BellCommand> bell,
    RailwayService railwayService
  ) {
    super(persistenceId);
    this.context = context;
    this.bell = bell;
    this.railwayService = railwayService;
  }

  @Override
  public GateState emptyState() {
    return new GateState(GateState.State.OPEN);
  }

  @Override
  public CommandHandler<GateCommand, GateEvent, GateState> commandHandler() {
    CommandHandlerBuilder<GateCommand, GateEvent, GateState> builder = newCommandHandlerBuilder();

    builder
      .forState(state -> state.getState() == GateState.State.OPEN)
      .onCommand(GateCommandClose.class, cmd ->
        Effect()
          .persist(List.of(new GateEventAdvanceState(), new GateEventClosed()))
          .thenRun(() -> railwayService.gateDown(context, context.getSelf().path().name()))
      );

    builder
      .forState(state -> state.getState() == GateState.State.CLOSED)
      .onCommand(GateCommandOpen.class, cmd ->
        Effect()
          .persist(List.of(new GateEventAdvanceState(), new GateEventOpened()))
          .thenRun(() -> railwayService.gateUp(context, context.getSelf().path().name()))
      );

    builder.forAnyState().onAnyCommand(cmd -> Effect().none());

    return builder.build();
  }

  @Override
  public EventHandler<GateState, GateEvent> eventHandler() {
    return newEventHandlerBuilder()
      .forAnyState()
      .onEvent(GateEventClosed.class, (state, event) -> {
        bell.tell(new Bell.CommandBellOn());
        context.getLog().info("Sent BellOn Command");
        return new GateState(state.getState());
      })
      .onEvent(GateEventOpened.class, (state, event) -> {
        bell.tell(new Bell.CommandBellOff());
        context.getLog().info("Sent BellOff Command");
        return new GateState(state.getState());
      })
      .onEvent(GateEventAdvanceState.class, (state, event) -> {
        context.getLog().info("Gate Advance State to {}", state.advanceState().getState());
        return (GateState) state.advanceState();
      })
      .onEvent(GateEvent.class, (state, gate) -> new GateState(state.getState()))
      .build();
  }
}
