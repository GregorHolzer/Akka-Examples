package actors.guardian;

import actors.light_machine.LightMachine;
import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import akka.actor.typed.receptionist.Receptionist;
import akka.actor.typed.receptionist.ServiceKey;
import akka.persistence.typed.PersistenceId;

public class LightMachineSetup extends AbstractBehavior<Receptionist.Listing> implements ComponentSetup {

    public static final String componentSuffix = "_LightMachine";

    private final String componentName;

    private final ServiceKey<LightMachine.LightMachineCommand>  lightMachineServiceKey;

    private final ActorRef<LightMachine.LightMachineCommand> lightMachine;

    public static Behavior<Receptionist.Listing> create(String crossingId) {
        return Behaviors.setup(context -> new LightMachineSetup(context, crossingId));
    }

    private LightMachineSetup(ActorContext<Receptionist.Listing> context, String crossingId) {
        super(context);
        this.componentName = crossingId + componentSuffix;
        lightMachineServiceKey =  ServiceKey.create(LightMachine.LightMachineCommand.class, componentName);
        lightMachine = getContext().spawn(LightMachine.create(PersistenceId.ofUniqueId(lightMachineServiceKey.toString())), String.format("LightMachine_of_service%s", componentName));
        getContext().getSystem().receptionist().tell(Receptionist.register(lightMachineServiceKey, lightMachine));
        getContext().getLog().info("LightMachine registered with ServiceKey: {}",  lightMachineServiceKey);
    }

    @Override
    public Receive<Receptionist.Listing> createReceive() {
        return newReceiveBuilder().build();
    }
}
