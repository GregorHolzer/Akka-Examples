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

    private final ServiceKey<Bell.BellCommand> bellServiceKey;

    private ActorRef<Bell.BellCommand> bell;

    public static Behavior<Receptionist.Listing> create(String serviceName) {
        return Behaviors.setup(context -> new BellSetup(context, serviceName));
    }

    private BellSetup(ActorContext<Receptionist.Listing> context, String serviceName) {
        super(context);
        bellServiceKey = ServiceKey.create(Bell.BellCommand.class, serviceName);
    }

    @Override
    public Receive<Receptionist.Listing> createReceive() {
        bell = getContext().spawn(Bell.create(PersistenceId.ofUniqueId(bellServiceKey.toString())), String.format("Bell_with_key_%s", bellServiceKey));
        getContext().getSystem().receptionist().tell(Receptionist.register(bellServiceKey, bell));
        getContext().getLog().info("Bell registered with ServiceKey: {}",  bellServiceKey);
        return newReceiveBuilder().build();
    }
}
