package actors.Detector;

import actors.Command;
import actors.Event;
import actors.Surveillance.Surveillance;
import actors.Surveillance.SurveillanceState;
import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.persistence.typed.PersistenceId;
import akka.persistence.typed.javadsl.CommandHandler;
import akka.persistence.typed.javadsl.CommandHandlerBuilder;
import akka.persistence.typed.javadsl.EventHandler;
import akka.persistence.typed.javadsl.EventSourcedBehavior;

public class Detector extends EventSourcedBehavior<Detector.DetectorCommand, Detector.DetectorEvent, DetectorState> {

    public interface DetectorCommand extends Command {}

    public static class CommandCaptured implements DetectorCommand {}

    public static class CommandTimeout implements DetectorCommand {}

    public static class CommandAlarm implements DetectorCommand {}

    public static class CommandDisarm implements DetectorCommand {}

    public interface DetectorEvent extends Event {}

    public static class EventCaptured implements DetectorEvent {}

    public static class EventTimeout implements DetectorEvent {}

    public static class EventAlarm implements DetectorEvent {}

    public static class EventDisarm implements DetectorEvent {}

    private final ActorContext<DetectorCommand> context;

    private final ActorRef<Surveillance.SurveillanceCommand> surveillance;

    public static Behavior<DetectorCommand> create(PersistenceId  persistenceId) {
        return Behaviors.setup(context -> {
            String surveillanceName = String.format("surveillance_%s", context.getSelf().path().name());
            ActorRef<Surveillance.SurveillanceCommand> surveillance = context.spawn(Surveillance.create(PersistenceId.ofUniqueId(surveillanceName), context.getSelf()), surveillanceName);
            return new Detector(persistenceId,context, surveillance);
        });
    }

    private Detector(PersistenceId persistenceId, ActorContext<DetectorCommand> context, ActorRef<Surveillance.SurveillanceCommand> surveillance) {
        super(persistenceId);
        this.context = context;
        this.surveillance = surveillance;
    }

    @Override
    public DetectorState emptyState() {
        return new DetectorState(DetectorState.State.Capturing);
    }

    @Override
    public CommandHandler<DetectorCommand, DetectorEvent, DetectorState> commandHandler() {
        CommandHandlerBuilder<DetectorCommand, DetectorEvent, DetectorState> builder = newCommandHandlerBuilder();

        builder.forState(state -> state.getState() == DetectorState.State.Capturing)
                .onCommand(CommandCaptured.class, cmd -> Effect().persist(new EventCaptured()));

        builder.forState(state -> state.getState() == DetectorState.State.Processing)
                .onCommand(CommandTimeout.class, t -> Effect().persist(new EventTimeout()))
                .onCommand(CommandAlarm.class, t -> Effect().persist(new EventAlarm()));

        builder.forState(state -> state.getState() == DetectorState.State.Alarm)
                .onCommand(CommandDisarm.class, t -> Effect().persist(new EventDisarm()));

        builder.forAnyState().onAnyCommand(cmd -> Effect().none());

        return builder.build();
    }

    @Override
    public EventHandler<DetectorState, DetectorEvent> eventHandler() {
        return newEventHandlerBuilder()
                .forAnyState()
                .onEvent(EventCaptured.class, (state, event) -> new DetectorState(DetectorState.State.Processing))
                .onEvent(EventTimeout.class, (state, event) -> new DetectorState(DetectorState.State.Capturing))
                .onEvent(EventAlarm.class, (state, event) -> new DetectorState(DetectorState.State.Alarm))
                .onEvent(EventDisarm.class, (state, event) -> new DetectorState(DetectorState.State.Capturing))
                .build();
    }
}