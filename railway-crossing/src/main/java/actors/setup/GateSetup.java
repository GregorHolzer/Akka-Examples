package actors.setup;

import actors.Bell;
import actors.Gate;
import actors.services.RailwayService;
import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import akka.actor.typed.receptionist.Receptionist;
import akka.actor.typed.receptionist.ServiceKey;
import java.util.List;

/**
 * GateSetup Actor:
 * <p>
 * <ul>
 *   <li> Discovers the {@link Bell} Actor via the {@link Receptionist}. </li>
 *   <li> Creates the {@link Gate} Actor once the {@link Bell} Actor is available. </li>
 *   <li> Registers the created {@link Gate} Actor in the {@link Receptionist}. </li>
 * </ul>
 * </p>
 */
public class GateSetup
  extends AbstractBehavior<Receptionist.Listing>
  implements ComponentSetup {

  /** Attached to the railway-crossing id to identify the component */
  public static final String componentSuffix = "_Gate";

  /** The ActorRef of the Gate */
  private ActorRef<Gate.GateCommand> gate;

  /** The ServiceKey to discover the Bell ActorRef from the Receptionist */
  private final ServiceKey<Bell.BellCommand> bellServiceKey;

  /** The ServiceKey used to register the Gate Actor in the Receptionist */
  private final ServiceKey<Gate.GateCommand> gateServiceKey;

  /** The railway-crossing-id of the Gate */
  private final String crossingId;

  /** Shared railway service used by the Gate */
  private final RailwayService railwayService;

  /**
   * Creates a new {@link GateSetup} Actor.
   *
   * @param crossingId the railway-crossing-id of the {@link Gate} Actor
   * @return the {@link Behavior} of the created {@link GateSetup} Actor
   */
  public static Behavior<Receptionist.Listing> create(String crossingId) {
    return Behaviors.setup(context -> new GateSetup(context, crossingId));
  }

  private GateSetup(
    ActorContext<Receptionist.Listing> context,
    String crossingId
  ) {
    super(context);
    this.crossingId = crossingId;
    this.railwayService = new RailwayService();

    // Create ServiceKeys for Bell discovery and Gate registration
    bellServiceKey = ServiceKey.create(
      Bell.BellCommand.class,
      crossingId + BellSetup.componentSuffix
    );
    gateServiceKey = ServiceKey.create(
      Gate.GateCommand.class,
      crossingId + componentSuffix
    );

    // Subscribe to the Receptionist to discover the Bell Actor
    getContext()
      .getSystem()
      .receptionist()
      .tell(Receptionist.subscribe(bellServiceKey, context.getSelf()));

    context.getLog().info("Gate subscribed to ServiceKeys: {}", bellServiceKey);
  }

  /**
   * Defines the {@link Behavior} of the {@link GateSetup} Actor that handles
   * messages from the {@link Receptionist}.
   */
  @Override
  public Receive<Receptionist.Listing> createReceive() {
    return newReceiveBuilder()
      .onMessage(Receptionist.Listing.class, this::onListing)
      .build();
  }

  /**
   * Handles messages of type {@link Receptionist.Listing} from the {@link Receptionist}.
   * <p>
   * When a {@link Bell} Actor is discovered, a {@link Gate} Actor is created and
   * registered with the {@link Receptionist}.
   *
   * @param listing message of the {@link Receptionist} that contains a list of {@link ActorRef}s
   */
  private Behavior<Receptionist.Listing> onListing(
    Receptionist.Listing listing
  ) {
    List<ActorRef<Bell.BellCommand>> availableBells = listing
      .getServiceInstances(bellServiceKey)
      .stream()
      .toList();

    // Extract Bell ActorRef if available
    ActorRef<Bell.BellCommand> bell = checkInstances(
      getContext(),
      availableBells,
      Bell.BellCommand.class
    );

    // Create and register the Gate once the Bell is discovered
    if (bell != null && gate == null) {
      gate = getContext().spawn(
        Gate.create(bell, railwayService),
        String.format("%s", crossingId + componentSuffix)
      );

      getContext()
        .getSystem()
        .receptionist()
        .tell(Receptionist.register(gateServiceKey, gate));

      getContext()
        .getLog()
        .info("Gate registered with ServiceKey: {}", gateServiceKey);
    }

    return Behaviors.same();
  }
}
