package actors.setup;

import actors.LightMachine;
import actors.common.RailwayService;
import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import akka.actor.typed.receptionist.Receptionist;
import akka.actor.typed.receptionist.ServiceKey;

/**
 * LightMachineSetup Actor:
 * <p>
 * <ul>
 *   <li>Creates the {@link LightMachine} Actor.</li>
 *   <li>Registers the {@link LightMachine} Actor with the {@link Receptionist} to enable discovery by other actors.</li>
 * </ul>
 * </p>
 */
public class LightMachineSetup
  extends AbstractBehavior<Receptionist.Listing>
  implements ComponentSetup {

  /** Attached to the railway-crossing id to identify the component */
  public static final String componentSuffix = "_LightMachine";

  /**
   * Creates a new {@link LightMachineSetup} Actor.
   *
   * @param crossingId the railway-crossing-id of the {@link LightMachine} Actor
   * @return the {@link Behavior} of the created {@link LightMachineSetup} Actor
   */
  public static Behavior<Receptionist.Listing> create(String crossingId) {
    return Behaviors.setup(context -> new LightMachineSetup(context, crossingId));
  }

  private LightMachineSetup(ActorContext<Receptionist.Listing> context, String crossingId) {
    super(context);
    String componentName = crossingId + componentSuffix;

    // Create the ServiceKey for the LightMachine Actor
    ServiceKey<LightMachine.LightMachineCommand> lightMachineServiceKey = ServiceKey.create(
      LightMachine.LightMachineCommand.class,
      componentName
    );

    // Spawn the LightMachine Actor
    ActorRef<LightMachine.LightMachineCommand> lightMachine = getContext().spawn(
      LightMachine.create(new RailwayService()),
      String.format("%s", componentName)
    );

    // Register the LightMachine Actor with the Receptionist
    getContext()
      .getSystem()
      .receptionist()
      .tell(Receptionist.register(lightMachineServiceKey, lightMachine));

    getContext()
      .getLog()
      .info("LightMachine registered with ServiceKey: {}", lightMachineServiceKey);
  }

  /**
   * Defines the {@link Behavior} of the {@link LightMachineSetup} Actor that handles no messages.
   */
  @Override
  public Receive<Receptionist.Listing> createReceive() {
    return newReceiveBuilder().build();
  }
}
