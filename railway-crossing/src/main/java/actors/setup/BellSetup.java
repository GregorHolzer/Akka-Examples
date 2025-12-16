package actors.setup;

import actors.Bell;
import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import akka.actor.typed.receptionist.Receptionist;
import akka.actor.typed.receptionist.ServiceKey;
import actors.common.RailwayService;

/**
 * BellSetup Actor: Creates the {@link Bell} Actor and enables Discovery of the {@link Bell} Actor
 */
public class BellSetup extends AbstractBehavior<Receptionist.Listing> {

  //attached to the railway-crossing id to identify the component
  public static final String componentSuffix = "_Bell";

  private BellSetup(
    ActorContext<Receptionist.Listing> context,
    String crossingId
  ) {
    super(context);
    //Create the ServiceKey for this Bell
    String componentName = crossingId + componentSuffix;
    ServiceKey<Bell.BellCommand> bellServiceKey = ServiceKey.create(
      Bell.BellCommand.class,
      componentName
    );
    //Create the Bell Actor
    ActorRef<Bell.BellCommand> bell = getContext().spawn(
      Bell.create(new RailwayService()),
      String.format("%s", componentName)
    );
    //Register the Bell Actor at the Receptionist for Discovery
    getContext().getSystem().receptionist().tell(Receptionist.register(bellServiceKey, bell));
    getContext().getLog().info("Bell registered with ServiceKey: {}", bellServiceKey);
  }

  /**
   * Creates a new {@link BellSetup} Actor
   * @param crossingId the railway-crossing-id of the {@link Bell} Actor
   * @return the {@link Behavior} of the created {@link BellSetup} Actor
   */
  public static Behavior<Receptionist.Listing> create(
          String crossingId
  ) {
    return Behaviors.setup(context -> new BellSetup(context, crossingId));
  }

  /** Defines the  {@link Behavior} of the {@link BellSetup} Actor that handles no messages */
  @Override
  public Receive<Receptionist.Listing> createReceive() {
    return newReceiveBuilder().build();
  }
}
