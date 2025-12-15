package actors.setup;

import actors.LightMachine;
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
 * LightMachineSetup Actor: Creates the {@link LightMachine} Actor and enables Discovery of the {@link LightMachine} Actor
 */
public class LightMachineSetup
  extends AbstractBehavior<Receptionist.Listing>
  implements ComponentSetup {

  public static final String componentSuffix = "_LightMachine";

  public static Behavior<Receptionist.Listing> create(
    String crossingId
  ) {
    return Behaviors.setup(context -> new LightMachineSetup(context, crossingId));
  }

  private LightMachineSetup(
    ActorContext<Receptionist.Listing> context,
    String crossingId
  ) {
    super(context);
    String componentName = crossingId + componentSuffix;
    ServiceKey<LightMachine.LightMachineCommand> lightMachineServiceKey = ServiceKey.create(
      LightMachine.LightMachineCommand.class,
      componentName
    );
    ActorRef<LightMachine.LightMachineCommand> lightMachine = getContext().spawn(
      LightMachine.create(new RailwayService()),
      String.format("%s", componentName)
    );
    getContext()
      .getSystem()
      .receptionist()
      .tell(Receptionist.register(lightMachineServiceKey, lightMachine));
    getContext()
      .getLog()
      .info("LightMachine registered with ServiceKey: {}", lightMachineServiceKey);
  }

  @Override
  public Receive<Receptionist.Listing> createReceive() {
    return newReceiveBuilder().build();
  }
}
