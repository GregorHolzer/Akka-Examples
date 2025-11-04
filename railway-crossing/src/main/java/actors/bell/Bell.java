package actors.bell;

import actors.Command;
import actors.Event;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.persistence.typed.PersistenceId;
import akka.persistence.typed.javadsl.CommandHandler;
import akka.persistence.typed.javadsl.CommandHandlerBuilder;
import akka.persistence.typed.javadsl.EventHandler;
import akka.persistence.typed.javadsl.EventSourcedBehavior;
import java.util.List;
import service.RailwayService;

public class Bell extends EventSourcedBehavior<Bell.BellCommand, Bell.BellEvent, BellState> {

  public interface BellCommand extends Command {}

  public static class CommandBellOn implements BellCommand {}

  public static class CommandBellOff implements BellCommand {}

  public interface BellEvent extends Event {}

  public static class EventAdvanceState implements BellEvent {}

  public static class EventBellOn implements BellEvent {}

  public static class EventBellOff implements BellEvent {}

  private final ActorContext<Bell.BellCommand> context;

  private final RailwayService railwayService;

  public static Behavior<BellCommand> create(
    PersistenceId persistenceId,
    RailwayService railwayService
  ) {
    return Behaviors.setup(context -> new Bell(persistenceId, context, railwayService));
  }

  private Bell(
    PersistenceId persistenceId,
    ActorContext<Bell.BellCommand> context,
    RailwayService railwayService
  ) {
    super(persistenceId);
    this.context = context;
    this.railwayService = railwayService;
  }

  @Override
  public BellState emptyState() {
    return new BellState(BellState.State.OFF);
  }

  @Override
  public CommandHandler<BellCommand, BellEvent, BellState> commandHandler() {
    CommandHandlerBuilder<BellCommand, BellEvent, BellState> builder = newCommandHandlerBuilder();

    builder
      .forState(state -> state.getState() == BellState.State.OFF)
      .onCommand(CommandBellOn.class, cmd ->
        Effect()
          .persist(List.of(new EventAdvanceState(), new EventBellOn()))
          .thenRun(() -> railwayService.bellOn(context, context.getSelf().path().name()))
      );

    builder
      .forState(state -> state.getState() == BellState.State.ON)
      .onCommand(CommandBellOff.class, cmd ->
        Effect()
          .persist(List.of(new EventAdvanceState(), new EventBellOff()))
          .thenRun(() -> railwayService.bellOff(context, context.getSelf().path().name()))
      );

    builder.forAnyState().onAnyCommand(cmd -> Effect().none());

    return builder.build();
  }

  @Override
  public EventHandler<BellState, BellEvent> eventHandler() {
    return newEventHandlerBuilder()
      .forAnyState()
      .onEvent(EventAdvanceState.class, (state, event) -> {
        context.getLog().info("Bell Advance State to {}", state.advanceState().getState());
        return (BellState) state.advanceState();
      })
      .onEvent(BellEvent.class, (state, event) -> state.createWithState(state.getState()))
      .build();
  }
}
