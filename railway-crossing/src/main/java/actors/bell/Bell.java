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
import service.RailwayService;

public class Bell extends EventSourcedBehavior<Bell.BellCommand, Bell.BellEvent, BellState> {

  /**
   * Defines the message-type {@link Bell} can receive
   */
  public interface BellCommand extends Command {}

  /**
   * Message that changes the {@link BellState} to {@link BellState.State#ON}
   */
  public static class CommandBellOn implements BellCommand {}

  /**
   * Message that changes the {@link BellState} to {@link BellState.State#OFF}
   */
  public static class CommandBellOff implements BellCommand {}

  /**
   * Defines the events that are persisted of {@link Bell}
   */
  public interface BellEvent extends Event {}

  /**
   * Event that states that the {@link BellState} has advanced:
   * {@link BellState.State#OFF} -> {@link BellState.State#ON} or
   * {@link BellState.State#ON} -> {@link BellState.State#OFF}
   */
  public static class EventAdvanceState implements BellEvent {}

  private final ActorContext<Bell.BellCommand> context;

  private final RailwayService railwayService;

  /**
   * Creates a new persistent {@link Bell} actor.
   *
   * @param persistenceId unique persistence identifier
   * @param railwayService service that is invoked upon messages @see {@link RailwayService}
   * @return a new {@link Behavior} instance for the {@link Bell} actor
   */
  public static Behavior<BellCommand> create(
    PersistenceId persistenceId,
    RailwayService railwayService
  ) {
    return Behaviors.setup(context -> new Bell(persistenceId, context, railwayService));
  }

  /**
   * Initializes the {@link Bell} actor.
   *
   * @param persistenceId unique persistence identifier
   * @param context {@link ActorContext} of the current Actor-System, provides information and methods to
   *                                    interact with the Actor-System
   * @param railwayService service that is invoked upon messages @see {@link RailwayService}
   */
  private Bell(
    PersistenceId persistenceId,
    ActorContext<Bell.BellCommand> context,
    RailwayService railwayService
  ) {
    super(persistenceId);
    this.context = context;
    this.railwayService = railwayService;
  }

  /**
   * Defines how to receive the initial {@link BellState}
   * @return initial {@link BellState}
   */
  @Override
  public BellState emptyState() {
    return new BellState(BellState.State.OFF);
  }

  /**
   * Defines how to handle {@link BellCommand}s:
   * On message {@link CommandBellOn} and in {@link BellState.State#OFF}:
   *  -> Persist new event {@link EventAdvanceState}
   *  -> invoke {@link RailwayService#bellOn(ActorContext, String)}
   *  On message {@link CommandBellOff} and in {@link BellState.State#ON}:
   *  -> Persist new event {@link EventAdvanceState}
   *  -> invoke {@link RailwayService#bellOff(ActorContext, String)}
   * @return {@link CommandHandler}
   */
  @Override
  public CommandHandler<BellCommand, BellEvent, BellState> commandHandler() {
    CommandHandlerBuilder<BellCommand, BellEvent, BellState> builder = newCommandHandlerBuilder();

    builder
      .forState(state -> state.getState() == BellState.State.OFF)
      .onCommand(CommandBellOn.class, cmd ->
        Effect()
          .persist(new EventAdvanceState())
          .thenRun(() -> railwayService.bellOn(context, context.getSelf().path().name()))
      );

    builder
      .forState(state -> state.getState() == BellState.State.ON)
      .onCommand(CommandBellOff.class, cmd ->
        Effect()
          .persist(new EventAdvanceState())
          .thenRun(() -> railwayService.bellOff(context, context.getSelf().path().name()))
      );

    builder.forAnyState().onAnyCommand(cmd -> Effect().none());

    return builder.build();
  }

  /**
   * Defines how to handle {@link BellEvent}s:
   * On event {@link EventAdvanceState}: update {@link BellState}
   * @return {@link EventHandler}
   */
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
