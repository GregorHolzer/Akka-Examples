package actors.setup;

import actors.Bell;
import actors.Gate;
import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import akka.actor.typed.receptionist.Receptionist;
import akka.actor.typed.receptionist.ServiceKey;
import java.util.List;
import service.RailwayService;

public class GateSetup extends AbstractBehavior<Receptionist.Listing> implements ComponentSetup {

  public static final String componentSuffix = "_Gate";

  private ActorRef<Gate.GateCommand> gate;

  private final ServiceKey<Bell.BellCommand> bellServiceKey;

  private final ServiceKey<Gate.GateCommand> gateServiceKey;

  private final String componentName;

  private final RailwayService railwayService;

  public static Behavior<Receptionist.Listing> create(
    String crossingId,
    RailwayService railwayService
  ) {
    return Behaviors.setup(context -> new GateSetup(context, crossingId, railwayService));
  }

  private GateSetup(
    ActorContext<Receptionist.Listing> context,
    String crossingId,
    RailwayService railwayService
  ) {
    super(context);
    this.componentName = crossingId + componentSuffix;
    bellServiceKey = ServiceKey.create(
      Bell.BellCommand.class,
      crossingId + BellSetup.componentSuffix
    );
    this.railwayService = railwayService;
    gateServiceKey = ServiceKey.create(Gate.GateCommand.class, componentName);
    getContext()
      .getSystem()
      .receptionist()
      .tell(Receptionist.subscribe(bellServiceKey, context.getSelf()));
    context.getLog().info("Gate subscribed to ServiceKeys: {}", bellServiceKey);
  }

  @Override
  public Receive<Receptionist.Listing> createReceive() {
    return newReceiveBuilder().onMessage(Receptionist.Listing.class, this::onListing).build();
  }

  private Behavior<Receptionist.Listing> onListing(Receptionist.Listing listing) {
    List<ActorRef<Bell.BellCommand>> availableBells = listing
      .getServiceInstances(bellServiceKey)
      .stream()
      .toList();
    ActorRef<Bell.BellCommand> bell = checkInstances(
      getContext(),
      availableBells,
      Bell.BellCommand.class
    );
    if (bell != null && gate == null) {
      gate = getContext().spawn(
        Gate.create(bell, railwayService),
        String.format("%s", componentName)
      );
      getContext().getSystem().receptionist().tell(Receptionist.register(gateServiceKey, gate));
      getContext().getLog().info("Gate registered with ServiceKey: {}", gateServiceKey);
    }
    return Behaviors.same();
  }
}
