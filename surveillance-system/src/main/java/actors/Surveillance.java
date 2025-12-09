package actors;

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
import java.util.HashSet;
import java.util.Set;
import services.SurveillanceService;

public class Surveillance extends AbstractBehavior<Surveillance.SurveillanceCommand> implements StateMachine<Surveillance.SurveillanceState> {

  private static final Object TIMEOUT_KEY = new Object();

  private final TimerScheduler<SurveillanceCommand> timers;

  private final ServiceKey<SurveillanceCommand> groupSurveillanceKey;

  private final ServiceKey<Detector.DetectorCommand> groupDetectorKey;

  private final ActorRef<Topic.Command<SurveillanceCommand>> surveillanceTopic;

  private final ActorRef<Topic.Command<Detector.DetectorCommand>> detectorTopic;

  private final SurveillanceService surveillanceService;

  private final Receive<SurveillanceCommand> processingBehaviour = newReceiveBuilder()
    .onMessage(FoundPersons.class, this::onFoundPersons)
    .onMessage(Analyzed.class, this::onAnalyzed)
    .onMessage(GlobalCommands.Alarm.class, msg -> onAlarm())
    .onMessage(WrappedListingMessage.class, this::onWrappedListing)
    .build();

  private final Receive<SurveillanceCommand> alarmBehaviour = newReceiveBuilder()
    .onMessage(GlobalCommands.Disarm.class, msg -> onDisarm())
    .onMessage(WrappedListingMessage.class, this::onWrappedListing)
    .build();

  private SurveillanceState surveillanceState = SurveillanceState.Processing;

  private Set<ActorRef<Detector.DetectorCommand>> groupDetectorRefs = new HashSet<>();

  private Set<ActorRef<SurveillanceCommand>> groupSurveillanceRefs = new HashSet<>();

    private Surveillance(
            ActorContext<Surveillance.SurveillanceCommand> context,
            TimerScheduler<SurveillanceCommand> timers,
            SurveillanceService surveillanceService,
            String groupId,
            String surveillanceId
    ) {
        super(context);
        this.timers = timers;
        this.surveillanceService = surveillanceService;
        groupSurveillanceKey = ServiceKey.create(SurveillanceCommand.class, groupId);
        groupDetectorKey = ServiceKey.create(Detector.DetectorCommand.class, groupId);
        ServiceKey<SurveillanceCommand> individualSurveillanceKey = ServiceKey.create(SurveillanceCommand.class, surveillanceId);
        //Register to be found by DetectorSetup
        getContext().getSystem().receptionist().tell(Receptionist.register(individualSurveillanceKey, getContext().getSelf()));
        //Adapter to receive Messages from Receptionist
        ActorRef<Receptionist.Listing> receptionistAdapter = getContext().messageAdapter(
                Receptionist.Listing.class,
                WrappedListingMessage::new
        );
        PubSub pubSub = PubSub.get(context.getSystem());
        surveillanceTopic = pubSub.topic(SurveillanceCommand.class, "global-surveillance-commands");
        surveillanceTopic.tell(Topic.subscribe(getContext().getSelf()));
        detectorTopic = pubSub.topic(Detector.DetectorCommand.class, "global-detector-commands");
        /*
        //Register to receive Global Messages from other Group Members
        getContext()
                .getSystem()
                .receptionist()
                .tell(Receptionist.register(groupSurveillanceKey, getContext().getSelf()));
        //Subscribe to Receptionist with Detector Key
        getContext()
                .getSystem()
                .receptionist()
                .tell(Receptionist.subscribe(groupDetectorKey, receptionistAdapter));
        //Subscribe to Receptionist with Group Surveillance Key
        getContext()
                .getSystem()
                .receptionist()
                .tell(Receptionist.subscribe(groupSurveillanceKey, receptionistAdapter));*/
    }

  public static Behavior<SurveillanceCommand> create(SurveillanceService surveillanceService, String groupId, String surveillanceId) {
    return Behaviors.withTimers(timers ->
      Behaviors.setup(context -> new Surveillance(context, timers, surveillanceService, groupId, surveillanceId))
    );
  }

  @Override
  public Receive<SurveillanceCommand> createReceive() {
    return processingBehaviour;
  }

  private Behavior<SurveillanceCommand> onFoundPersons(FoundPersons persons) {
    if (surveillanceState == SurveillanceState.Processing) {
      surveillanceService.analyze(getContext(), persons);
      return processingBehaviour;
    }
    return Behaviors.same();
  }

  private Behavior<SurveillanceCommand> onAnalyzed(Analyzed analyzed) {
    if (surveillanceState == SurveillanceState.Processing) {
      if (analyzed.hasThreat) {
        getContext().getSelf().tell(new GlobalCommands.Alarm());
        surveillanceTopic.tell(Topic.publish(new GlobalCommands.Alarm()));
        detectorTopic.tell(Topic.publish(new GlobalCommands.Alarm()));
        //Alarm all Group DetectorActors
        //groupDetectorRefs.forEach(ref -> ref.tell(new GlobalCommands.Alarm()));
        //Alarm all Group SurveillanceActors
        //groupSurveillanceRefs.forEach(ref -> ref.tell(new GlobalCommands.Alarm()));
      }
      return processingBehaviour;
    }
    return Behaviors.same();
  }

  private Behavior<SurveillanceCommand> onAlarm() {
    if (surveillanceState == SurveillanceState.Processing) {
      surveillanceState = SurveillanceState.Alarm;
      logState(getContext(), surveillanceState);
      timers.startSingleTimer(new GlobalCommands.Disarm(), Duration.ofMillis(10000));
    }
    return alarmBehaviour;
  }

  private Behavior<SurveillanceCommand> onDisarm() {
    if (surveillanceState == SurveillanceState.Alarm) {
      surveillanceState = SurveillanceState.Processing;
      logState(getContext(), surveillanceState);
      surveillanceTopic.tell(Topic.publish(new GlobalCommands.Disarm()));
      detectorTopic.tell(Topic.publish(new GlobalCommands.Disarm()));
      /*//Disarm all DetectorActors
      groupDetectorRefs.forEach(ref -> ref.tell(new GlobalCommands.Disarm()));
      //Disarm all SurveillanceActors
      groupSurveillanceRefs.forEach(ref -> ref.tell(new GlobalCommands.Disarm()));*/
      timers.cancel(TIMEOUT_KEY);
      return processingBehaviour;
    }
    return Behaviors.same();
  }

  private Behavior<SurveillanceCommand> onWrappedListing(WrappedListingMessage wrappedListing) {
    Receptionist.Listing listing = wrappedListing.listing;
    if (listing.isForKey(groupDetectorKey)) {
      this.groupDetectorRefs = new HashSet<>(
        listing.getServiceInstances(groupDetectorKey)
      );
    }
    if (listing.isForKey(groupSurveillanceKey)) {
      this.groupSurveillanceRefs = new HashSet<>(
        listing.getServiceInstances(groupSurveillanceKey)
                .stream()
                .filter(ref -> !getContext().getSelf().equals(ref)).toList()
      );
    }
    return Behaviors.same();
  }

    public enum SurveillanceState {
        Processing,
        Alarm
    }

    public interface SurveillanceCommand extends Command {}

    public static class FoundPersons implements SurveillanceCommand {

        public byte[] image;

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

  private record WrappedListingMessage(Receptionist.Listing listing) implements SurveillanceCommand {

    @JsonCreator
    private WrappedListingMessage(@JsonProperty Receptionist.Listing listing) {
      this.listing = listing;
    }
  }
}
