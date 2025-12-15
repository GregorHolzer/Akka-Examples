package actors;

import actors.common.Command;
import actors.common.GlobalCommands;
import actors.common.StateMachine;
import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.*;
import akka.actor.typed.pubsub.PubSub;
import akka.actor.typed.pubsub.Topic;
import akka.actor.typed.receptionist.Receptionist;
import akka.actor.typed.receptionist.ServiceKey;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Duration;
import services.SurveillanceService;

public class Surveillance extends AbstractBehavior<Surveillance.SurveillanceCommand> implements StateMachine<Surveillance.SurveillanceState> {

  private static final Object TIMEOUT_KEY = new Object();

  private final TimerScheduler<SurveillanceCommand> timers;

  private final ActorRef<Topic.Command<SurveillanceCommand>> surveillanceTopic;

  private final ActorRef<Topic.Command<Detector.DetectorCommand>> detectorTopic;

  private final SurveillanceService surveillanceService;

  private SurveillanceState surveillanceState = SurveillanceState.Processing;

    private Surveillance(
            ActorContext<Surveillance.SurveillanceCommand> context,
            TimerScheduler<SurveillanceCommand> timers,
            SurveillanceService surveillanceService,
            String surveillanceId
    ) {
        super(context);
        this.timers = timers;
        this.surveillanceService = surveillanceService;
        ServiceKey<SurveillanceCommand> individualSurveillanceKey = ServiceKey.create(SurveillanceCommand.class, surveillanceId);
        //Register to be found by DetectorSetup
        getContext().getSystem().receptionist().tell(Receptionist.register(individualSurveillanceKey, getContext().getSelf()));
        PubSub pubSub = PubSub.get(context.getSystem());
        surveillanceTopic = pubSub.topic(SurveillanceCommand.class, "global-surveillance-commands");
        surveillanceTopic.tell(Topic.subscribe(getContext().getSelf()));
        detectorTopic = pubSub.topic(Detector.DetectorCommand.class, "global-detector-commands");
    }

  public static Behavior<SurveillanceCommand> create(SurveillanceService surveillanceService, String surveillanceId) {
    return Behaviors.withTimers(timers ->
      Behaviors.setup(context -> new Surveillance(context, timers, surveillanceService, surveillanceId))
    );
  }

  @Override
  public Receive<SurveillanceCommand> createReceive() {
    return newReceiveBuilder()
            .onMessage(FoundPersons.class, this::onFoundPersons)
            .onMessage(Analyzed.class, this::onAnalyzed)
            .onMessage(GlobalCommands.Alarm.class, msg -> onAlarm())
            .onMessage(GlobalCommands.Disarm.class, msg -> onDisarm())
            .build();
  }

  private Behavior<SurveillanceCommand> onFoundPersons(FoundPersons persons) {
    if (surveillanceState == SurveillanceState.Processing) {
      surveillanceService.analyze(getContext(), persons);
    }
    return Behaviors.same();
  }

  private Behavior<SurveillanceCommand> onAnalyzed(Analyzed analyzed) {
    if (surveillanceState == SurveillanceState.Processing) {
      if (analyzed.hasThreat) {
        //getContext().getSelf().tell(new GlobalCommands.Alarm());
        surveillanceTopic.tell(Topic.publish(new GlobalCommands.Alarm()));
        detectorTopic.tell(Topic.publish(new GlobalCommands.Alarm()));
      }
    }
    return Behaviors.same();
  }

  private Behavior<SurveillanceCommand> onAlarm() {
    if (surveillanceState == SurveillanceState.Processing) {
      surveillanceState = SurveillanceState.Alarm;
      logState(getContext(), surveillanceState);
      timers.startSingleTimer(new GlobalCommands.Disarm(), Duration.ofMillis(10000));
    }
    return Behaviors.same();
  }

  private Behavior<SurveillanceCommand> onDisarm() {
    if (surveillanceState == SurveillanceState.Alarm) {
      surveillanceState = SurveillanceState.Processing;
      logState(getContext(), surveillanceState);
      surveillanceTopic.tell(Topic.publish(new GlobalCommands.Disarm()));
      detectorTopic.tell(Topic.publish(new GlobalCommands.Disarm()));
      timers.cancel(TIMEOUT_KEY);
    }
    return Behaviors.same();
  }

    public enum SurveillanceState {
        Processing,
        Alarm
    }

    public interface SurveillanceCommand extends Command {}

    public record FoundPersons(byte[] image) implements SurveillanceCommand {

        @JsonCreator
        public FoundPersons(@JsonProperty("image") byte[] image) {
            this.image = image;
        }
    }

  public record Analyzed(byte[] image, Boolean hasThreat) implements SurveillanceCommand {

    @JsonCreator
    public Analyzed(@JsonProperty("image") byte[] image, Boolean hasThreat) {
      this.image = image;
      this.hasThreat = hasThreat;
    }
  }
}
