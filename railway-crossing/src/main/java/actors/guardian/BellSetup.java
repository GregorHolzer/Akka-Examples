package actors.guardian;

import actors.bell.Bell;
import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import akka.actor.typed.receptionist.Receptionist;
import akka.actor.typed.receptionist.ServiceKey;
import akka.persistence.typed.PersistenceId;

public class BellSetup extends AbstractBehavior<Receptionist.Listing> {

    public final static String serviceSuffix = "_Bell";

    private final String serviceName;

    private final ServiceKey<Bell.BellCommand> bellServiceKey;

    private ActorRef<Bell.BellCommand> bell;

    public static Behavior<Receptionist.Listing> create(String serviceId) {
        return Behaviors.setup(context -> new BellSetup(context, serviceId));
    }

    private BellSetup(ActorContext<Receptionist.Listing> context, String serviceId) {
        super(context);
        this.serviceName = serviceId + serviceSuffix;
        bellServiceKey = ServiceKey.create(Bell.BellCommand.class, serviceName);
        bell = getContext().spawn(Bell.create(PersistenceId.ofUniqueId(bellServiceKey.toString())), String.format("Bell_of_service_%s", serviceName));
        getContext().getSystem().receptionist().tell(Receptionist.register(bellServiceKey, bell));
        getContext().getLog().info("Bell registered with ServiceKey: {}",  bellServiceKey);
    }

    @Override
    public Receive<Receptionist.Listing> createReceive() {
        return newReceiveBuilder().build();
    }
}
