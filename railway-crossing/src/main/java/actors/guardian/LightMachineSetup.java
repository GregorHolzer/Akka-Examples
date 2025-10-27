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

    public static final String serviceSuffix = "_LightMachine";

    private final String serviceName;

    private final ServiceKey<LightMachine.LightMachineCommand>  lightMachineServiceKey;

    private final ActorRef<LightMachine.LightMachineCommand> lightMachine;

    public static Behavior<Receptionist.Listing> create(String serviceId) {
        return Behaviors.setup(context -> new LightMachineSetup(context, serviceId));
    }

    private LightMachineSetup(ActorContext<Receptionist.Listing> context, String serviceId) {
        super(context);
        this.serviceName = serviceId + serviceSuffix;
        lightMachineServiceKey =  ServiceKey.create(LightMachine.LightMachineCommand.class, serviceName);
        lightMachine = getContext().spawn(LightMachine.create(PersistenceId.ofUniqueId(lightMachineServiceKey.toString())), String.format("LightMachine_of_service%s", serviceName));
        getContext().getSystem().receptionist().tell(Receptionist.register(lightMachineServiceKey, lightMachine));
        getContext().getLog().info("LightMachine registered with ServiceKey: {}",  lightMachineServiceKey);
    }

    @Override
    public Receive<Receptionist.Listing> createReceive() {
        return newReceiveBuilder().build();
    }
}
