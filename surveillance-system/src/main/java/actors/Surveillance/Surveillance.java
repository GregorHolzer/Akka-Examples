package actors.Surveillance;

import actors.Command;
import actors.Detector.Detector;
import actors.global_commands.GlobalCommands;
import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.internal.receptionist.ReceptionistMessages;
import akka.actor.typed.javadsl.*;
import akka.actor.typed.receptionist.Receptionist;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import services.SurveillanceServices;

public class Surveillance extends AbstractBehavior<Surveillance.SurveillanceCommand> {

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

  public static class Analyzed implements SurveillanceCommand {

    public final ImageWrapper wrapper;

    @JsonCreator
    public Analyzed(@JsonProperty("wrapper") ImageWrapper wrapper) {
      this.wrapper = wrapper;
    }
  }

  public static class ImageWrapper {

    public final byte[] image;

    public Boolean hasThread = false;

    @JsonCreator
    public ImageWrapper(@JsonProperty("image") byte[] image) {
      this.image = image;
    }
  }

  private static class WrappedListingMessage implements SurveillanceCommand {

    public final Receptionist.Listing listing;

    @JsonCreator
    private WrappedListingMessage(@JsonProperty Receptionist.Listing listing) {
      this.listing = listing;
    }
  }

  private static final Object TIMEOUT_KEY = new Object();

  private final TimerScheduler<SurveillanceCommand> timers;

  private final ActorRef<Receptionist.Listing> receptionistAdater;

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

  private Set<ActorRef<Detector.DetectorCommand>> detectorRefs = new HashSet<>();

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
    this.receptionistAdater = getContext().messageAdapter(
      Receptionist.Listing.class,
      WrappedListingMessage::new
    );
    //Subscribe to Receptionist with Detector Key
    getContext()
      .getSystem()
      .receptionist()
      .tell(Receptionist.subscribe(Detector.receptionist_detector_key, receptionistAdater));
  }

  @Override
  public Receive<SurveillanceCommand> createReceive() {
    return processingBehaviour;
  }

  private Behavior<SurveillanceCommand> onFoundPersons(FoundPersons persons) {
    if (surveillanceState == SurveillanceState.Processing) {
      ImageWrapper wrapper = new ImageWrapper(persons.image);
      surveillanceServices.analyze(getContext(), wrapper);
      return processingBehaviour;
    }
    return Behaviors.same();
  }

  private Behavior<SurveillanceCommand> onAnalyzed(Analyzed analyzed) {
    if (surveillanceState == SurveillanceState.Processing) {
      if (analyzed.wrapper.hasThread) {
        getContext().getSelf().tell(new GlobalCommands.Alarm());
        //tell all known Detectors the Alarm
        detectorRefs.forEach(ref -> {
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
      //tell all known Detectors the Disarm
      detectorRefs.forEach(ref -> {
        ref.tell(new GlobalCommands.Disarm());
      });
      timers.cancel(TIMEOUT_KEY);
      return processingBehaviour;
    }
    return Behaviors.same();
  }

  private Behavior<SurveillanceCommand> onWrappedListing(WrappedListingMessage wrappedListing) {
    Receptionist.Listing listing = wrappedListing.listing;
    this.detectorRefs = new HashSet<>(
      listing.getServiceInstances(Detector.receptionist_detector_key)
    );
    return Behaviors.same();
  }
}
