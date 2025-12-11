package actors;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import akka.actor.typed.receptionist.Receptionist;
import akka.actor.typed.receptionist.ServiceKey;
import services.DetectorService;

import java.util.List;

public class DetectorSetup extends AbstractBehavior<Receptionist.Listing> {

    private ActorRef<Detector.DetectorCommand> detector = null;

    private final ServiceKey<Surveillance.SurveillanceCommand> individual_surveillance_key;

    private final String detectorId;

    private final Integer cameraId;

    private final DetectorService detectorService;

    private DetectorSetup(ActorContext<Receptionist.Listing> context, String detectorId, String surveillanceId, Integer cameraId) {
        super(context);
        this.detectorId = detectorId;
        individual_surveillance_key = ServiceKey.create(
                Surveillance.SurveillanceCommand.class, surveillanceId
        );
        this.cameraId = cameraId;
        //maybe read from config?
        detectorService = new DetectorService();
        getContext().getSystem().receptionist().tell(Receptionist.subscribe(individual_surveillance_key, getContext().getSelf()));
    }

    public static Behavior<Receptionist.Listing> create(String detectorId, String surveillanceId, Integer cameraId) {
        return Behaviors.setup(context -> new DetectorSetup(context,detectorId, surveillanceId, cameraId));
    }

    @Override
    public Receive<Receptionist.Listing> createReceive() {
        return newReceiveBuilder()
                .onMessage(Receptionist.Listing.class,  this::onListing)
                .build();
    }

    private Behavior<Receptionist.Listing> onListing(Receptionist.Listing listing) {
        List<ActorRef<Surveillance.SurveillanceCommand>> availableSurveillance = listing.getServiceInstances(individual_surveillance_key)
                .stream().toList();
        if(detector == null && !availableSurveillance.isEmpty()) {
            getContext().getLog().info("Registered detector with id {}", detectorId);
            detector = getContext().spawn(Detector.create(cameraId, availableSurveillance.getFirst(), detectorService), detectorId);
        }
        return Behaviors.same();
    }
}
