package actors;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.*;
import akka.actor.typed.receptionist.Receptionist;
import akka.actor.typed.receptionist.ServiceKey;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import services.SurveillanceServices;

public class Surveillance extends AbstractBehavior<Surveillance.SurveillanceCommand> {

  public interface SurveillanceCommand extends Command {}

  public static class FoundPersons implements SurveillanceCommand {

    public byte[] image;

    @JsonCreator
    public FoundPersons(@JsonProperty("image") byte[] image) {
      this.image = image;
    }
  }

  public static class Analyzed implements SurveillanceCommand {

    public final byte[] image;

    public final Boolean hasThread;

    @JsonCreator
    public Analyzed(@JsonProperty("image") byte[] image, Boolean hasThread) {
      this.image = image;
      this.hasThread = hasThread;
    }
  }

  public enum SurveillanceState {
    Processing,
    Alarm
  }

  public static final ServiceKey<SurveillanceCommand> receptionist_surveillance_key =
    ServiceKey.create(SurveillanceCommand.class, "GLOBAL_SURVEILLANCE_KEY");

  private static class WrappedListingMessage implements SurveillanceCommand {

    public final Receptionist.Listing listing;

    @JsonCreator
    private WrappedListingMessage(@JsonProperty Receptionist.Listing listing) {
      this.listing = listing;
    }
  }

  private static final Object TIMEOUT_KEY = new Object();

  private final TimerScheduler<SurveillanceCommand> timers;

  private final SurveillanceServices surveillanceServices;

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

  private Set<ActorRef<Detector.DetectorCommand>> allDetectorRefs = new HashSet<>();

  private Set<ActorRef<SurveillanceCommand>> allSurveillanceRefs = new HashSet<>();

  public static Behavior<SurveillanceCommand> create(SurveillanceServices surveillanceServices) {
    return Behaviors.withTimers(timers ->
      Behaviors.setup(context -> new Surveillance(context, timers, surveillanceServices))
    );
  }

  private Surveillance(
    ActorContext<Surveillance.SurveillanceCommand> context,
    TimerScheduler<SurveillanceCommand> timers,
    SurveillanceServices surveillanceServices
  ) {
    super(context);
    this.timers = timers;
    this.surveillanceServices = surveillanceServices;
    //Adapter to receive Messages from Receptionist
    ActorRef<Receptionist.Listing> receptionistAdapter = getContext().messageAdapter(
      Receptionist.Listing.class,
      WrappedListingMessage::new
    );
    //Register to receive Global Messages
    getContext()
      .getSystem()
      .receptionist()
      .tell(Receptionist.register(receptionist_surveillance_key, getContext().getSelf()));
    //Subscribe to Receptionist with Detector Key
    getContext()
      .getSystem()
      .receptionist()
      .tell(Receptionist.subscribe(Detector.receptionist_detector_key, receptionistAdapter));
    //Subscribe to Receptionist with Surveillance Key
    getContext()
      .getSystem()
      .receptionist()
      .tell(Receptionist.subscribe(receptionist_surveillance_key, receptionistAdapter));
  }

  @Override
  public Receive<SurveillanceCommand> createReceive() {
    return processingBehaviour;
  }

  private Behavior<SurveillanceCommand> onFoundPersons(FoundPersons persons) {
    if (surveillanceState == SurveillanceState.Processing) {
      surveillanceServices.analyze(getContext(), persons);
      return processingBehaviour;
    }
    return Behaviors.same();
  }

  private Behavior<SurveillanceCommand> onAnalyzed(Analyzed analyzed) {
    if (surveillanceState == SurveillanceState.Processing) {
      if (analyzed.hasThread) {
        getContext().getSelf().tell(new GlobalCommands.Alarm());
        //Alarm all DetectorActors
        allDetectorRefs.forEach(ref -> {
          ref.tell(new GlobalCommands.Alarm());
        });
        //Alarm all SurveillanceActors
        allSurveillanceRefs.forEach(ref -> {
          ref.tell(new GlobalCommands.Alarm());
        });
      }
      return processingBehaviour;
    }
    return Behaviors.same();
  }

  private Behavior<SurveillanceCommand> onAlarm() {
    if (surveillanceState == SurveillanceState.Processing) {
      surveillanceState = SurveillanceState.Alarm;
      timers.startSingleTimer(new GlobalCommands.Disarm(), Duration.ofMillis(10000));
    }
    return alarmBehaviour;
  }

  private Behavior<SurveillanceCommand> onDisarm() {
    if (surveillanceState == SurveillanceState.Alarm) {
      surveillanceState = SurveillanceState.Processing;
      //Disarm all DetectorActors
      allDetectorRefs.forEach(ref -> {
        ref.tell(new GlobalCommands.Disarm());
      });
      //Disarm all SurveillanceActors
      allSurveillanceRefs.forEach(ref -> {
        ref.tell(new GlobalCommands.Disarm());
      });
      timers.cancel(TIMEOUT_KEY);
      return processingBehaviour;
    }
    return Behaviors.same();
  }

  private Behavior<SurveillanceCommand> onWrappedListing(WrappedListingMessage wrappedListing) {
    Receptionist.Listing listing = wrappedListing.listing;
    if (listing.isForKey(Detector.receptionist_detector_key)) {
      this.allDetectorRefs = new HashSet<>(
        listing.getServiceInstances(Detector.receptionist_detector_key)
      );
    }
    if (listing.isForKey(receptionist_surveillance_key)) {
      this.allSurveillanceRefs = new HashSet<>(
        listing.getServiceInstances(receptionist_surveillance_key)
      );
    }
    return Behaviors.same();
  }
}
