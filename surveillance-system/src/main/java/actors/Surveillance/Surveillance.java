package actors.Surveillance;

import actors.Command;
import actors.Detector.Detector;
import actors.Event;
import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.persistence.typed.PersistenceId;
import akka.persistence.typed.javadsl.CommandHandler;
import akka.persistence.typed.javadsl.CommandHandlerBuilder;
import akka.persistence.typed.javadsl.EventHandler;
import akka.persistence.typed.javadsl.EventSourcedBehavior;
import com.fasterxml.jackson.annotation.JsonCreator;

public class Surveillance extends EventSourcedBehavior<Surveillance.SurveillanceCommand, Surveillance.SurveillanceEvent, SurveillanceState> {

    public interface SurveillanceCommand extends Command {}

    public static class CommandFoundPerson implements SurveillanceCommand {}

    public static class CommandAnalyzed implements SurveillanceCommand {

        private final Boolean hasThread;

        @JsonCreator
        public CommandAnalyzed(Boolean hasThread) {
            this.hasThread = hasThread;
        }

        public Boolean getHasThread() {
            return hasThread;
        }
    }

    public static class CommandAlarm implements SurveillanceCommand {}

    public static class CommandDisarm implements SurveillanceCommand {}

    public interface SurveillanceEvent extends Event {}

    public static class EventFoundPerson implements SurveillanceEvent {}

    public static class EventAnalyzed implements SurveillanceEvent {

        private final Boolean hasThread;

        @JsonCreator
        public EventAnalyzed(Boolean hasThread) {
            this.hasThread = hasThread;
        }

        public Boolean getHasThread() {
            return hasThread;
        }
    }

    public static class EventAlarm implements SurveillanceEvent {}

    public static class EventDisarm implements SurveillanceEvent {}

    private final ActorContext<SurveillanceCommand> context;

    private final ActorRef<Detector.DetectorCommand> detector;

    public static Behavior<SurveillanceCommand> create(PersistenceId persistenceId, ActorRef<Detector.DetectorCommand> detector){
        return Behaviors.setup(context -> {
            return new Surveillance(persistenceId, context, detector);
        });
    }

    private Surveillance(PersistenceId persistenceId, ActorContext<SurveillanceCommand> context, ActorRef<Detector.DetectorCommand> detector) {
        super(persistenceId);
        this.context = context;
        this.detector = detector;
    }

    @Override
    public SurveillanceState emptyState() {
        return new SurveillanceState(SurveillanceState.State.Analyzing);
    }

    @Override
    public CommandHandler<SurveillanceCommand, SurveillanceEvent, SurveillanceState> commandHandler() {
        CommandHandlerBuilder<SurveillanceCommand, SurveillanceEvent, SurveillanceState> builder = new CommandHandlerBuilder<>();

        builder.forState(state -> state.getState() ==  SurveillanceState.State.Analyzing)
                .onCommand(CommandFoundPerson.class, cmd -> Effect().persist(new EventFoundPerson()))
                .onCommand(CommandAnalyzed.class, cmd -> Effect().persist(new EventAnalyzed(cmd.getHasThread())))
                .onCommand(CommandAlarm.class, cmd -> Effect().persist(new EventAlarm()));

        builder.forState(state -> state.getState() == SurveillanceState.State.Alarm)
                        .onCommand(CommandDisarm.class, cmd -> Effect().persist(new EventDisarm()));

        builder.forAnyState().onAnyCommand(cmd -> Effect().none());

        return builder.build();
    }

    @Override
    public EventHandler<SurveillanceState, SurveillanceEvent> eventHandler() {
        return newEventHandlerBuilder()
                .forAnyState()
                .onEvent(EventFoundPerson.class, (state, event) -> {
                    //Todo: invoke analyze
                    return new SurveillanceState(SurveillanceState.State.Analyzing);
                })
                .onEvent(EventAnalyzed.class, (state, event) -> {
                    if(event.getHasThread()) {
                        context.getSelf().tell(new CommandAlarm());
                        detector.tell(new Detector.CommandAlarm());
                    }
                    return new SurveillanceState(SurveillanceState.State.Analyzing);
                })
                .onEvent(EventAlarm.class, (state, event) -> new SurveillanceState(SurveillanceState.State.Alarm))
                .onEvent(EventDisarm.class, (state, event ) -> new SurveillanceState(SurveillanceState.State.Analyzing))
                .build();
    }
}