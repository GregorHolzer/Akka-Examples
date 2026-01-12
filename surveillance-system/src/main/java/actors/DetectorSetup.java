package actors;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import akka.actor.typed.receptionist.Receptionist;
import akka.actor.typed.receptionist.ServiceKey;
import java.util.List;
import services.DetectorService;

/**
 * DetectorSetup Actor:
 * <lu>
 * <li>Subscribes to the Receptionist and waits for the Surveillance Actor.</li>
 * <li>Spawns a Detector actor once the Surveillance Actor is ready.</li>
 * </lu>
 */
public class DetectorSetup extends AbstractBehavior<Receptionist.Listing> {

  /**
   * The ActorRef of the created Detector
   */
  private ActorRef<Detector.DetectorCommand> detector = null;

  /**
   * The Receptionist Key of the Surveillance Actor
   */
  private final ServiceKey<
    Surveillance.SurveillanceCommand
  > individual_surveillance_key;

  /**
   * The id of the {@link Detector} Actor
   */
  private final String detectorId;

  /**
   * The id of the {@link Surveillance} Actor that receives {@link Surveillance.FoundPersons} messages
   */
  private final String surveillanceId;

  /** The cameraId that is passed to the Camera-Capture service */
  private final Integer cameraId;

  /** Provides functionality to invoke detector-services */
  private final DetectorService detectorService;

  private DetectorSetup(
    ActorContext<Receptionist.Listing> context,
    String detectorId,
    String surveillanceId,
    Integer cameraId
  ) {
    super(context);
    this.detectorId = detectorId;
    this.surveillanceId = surveillanceId;
    individual_surveillance_key = ServiceKey.create(
      Surveillance.SurveillanceCommand.class,
      surveillanceId
    );
    this.cameraId = cameraId;
    detectorService = new DetectorService();
    //Subscribe to the Receptionist to discover the Surveillance Actor
    getContext()
      .getSystem()
      .receptionist()
      .tell(
        Receptionist.subscribe(
          individual_surveillance_key,
          getContext().getSelf()
        )
      );
  }

  /**
   * Creates the {@link DetectorSetup} Actor.
   *
   * @param detectorId the id of the {@link DetectorSetup} Actor.
   * @param surveillanceId the id of the {@link Surveillance} Actor.
   * @param cameraId the id of the camera that is passed to the IOT-Service for capturing images.
   * @return the {@link Behavior} of the created {@link DetectorSetup}
   */
  public static Behavior<Receptionist.Listing> create(
    String detectorId,
    String surveillanceId,
    Integer cameraId
  ) {
    return Behaviors.setup(context ->
      new DetectorSetup(context, detectorId, surveillanceId, cameraId)
    );
  }

  /** Defines the {@link Behavior} of the DetectorSetup Actor that handles {@link Receptionist.Listing} messages from the {@link Receptionist} */
  @Override
  public Receive<Receptionist.Listing> createReceive() {
    return newReceiveBuilder()
      .onMessage(Receptionist.Listing.class, this::onListing)
      .build();
  }

  /**
   * Handles Messages from the Receptionist.
   *
   * @param listing a message from the {@link Receptionist}  that contains a list of {@link ActorRef} s that have been registered with the {@link #individual_surveillance_key}
   */
  private Behavior<Receptionist.Listing> onListing(
    Receptionist.Listing listing
  ) {
    List<ActorRef<Surveillance.SurveillanceCommand>> availableSurveillance =
      listing
        .getServiceInstances(individual_surveillance_key)
        .stream()
        .toList();
    if (detector == null && !availableSurveillance.isEmpty()) {
      getContext().getLog().info("Registered detector with id {}", detectorId);
      detector = getContext().spawn(
        Detector.create(
          cameraId,
          availableSurveillance.getFirst(),
          detectorService
        ),
        detectorId
      );
    } else if (detector == null) {
      getContext()
        .getLog()
        .warn(
          "No Surveillance Actor with surveillanceId {} found",
          surveillanceId
        );
    }
    return Behaviors.same();
  }
}
