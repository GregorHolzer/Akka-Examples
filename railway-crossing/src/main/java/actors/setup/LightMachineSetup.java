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
import service.RailwayService;

public class LightMachineSetup
  extends AbstractBehavior<Receptionist.Listing>
  implements ComponentSetup {

  public static final String componentSuffix = "_LightMachine";

  public static Behavior<Receptionist.Listing> create(
    String crossingId,
    RailwayService railwayService
  ) {
    return Behaviors.setup(context -> new LightMachineSetup(context, crossingId, railwayService));
  }

  private LightMachineSetup(
    ActorContext<Receptionist.Listing> context,
    String crossingId,
    RailwayService railwayService
  ) {
    super(context);
    String componentName = crossingId + componentSuffix;
    ServiceKey<LightMachine.LightMachineCommand> lightMachineServiceKey = ServiceKey.create(
      LightMachine.LightMachineCommand.class,
      componentName
    );
    ActorRef<LightMachine.LightMachineCommand> lightMachine = getContext().spawn(
      LightMachine.create(railwayService),
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
