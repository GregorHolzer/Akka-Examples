package actors.guardian;

import actors.bell.Bell;
import actors.gate.Gate;
import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import akka.actor.typed.receptionist.Receptionist;
import akka.actor.typed.receptionist.ServiceKey;
import akka.persistence.typed.PersistenceId;

import java.util.List;

public class GateSetup extends AbstractBehavior<Receptionist.Listing> implements ComponentSetup{

    private ActorRef<Bell.BellCommand> bell;

    private ActorRef<Gate.GateCommand> gate;

    private final ServiceKey<Bell.BellCommand> bellServiceKey;

    private final ServiceKey<Gate.GateCommand> gateServiceKey;

    public static Behavior<Receptionist.Listing> create(String serviceName) {
        return Behaviors.setup(context -> new GateSetup(context,serviceName));
    }

    private GateSetup(ActorContext<Receptionist.Listing> context, String serviceName) {
        super(context);
        bellServiceKey = ServiceKey.create(Bell.BellCommand.class, serviceName);
        gateServiceKey = ServiceKey.create(Gate.GateCommand.class, serviceName);
        getContext().getSystem().receptionist().tell(Receptionist.subscribe(bellServiceKey, context.getSelf()));
        context.getLog().info("Gate subscribed to ServiceKeys: {}",  bellServiceKey);
    }

    @Override
    public Receive<Receptionist.Listing> createReceive() {
        return newReceiveBuilder()
                .onMessage(Receptionist.Listing.class, this::onListing)
                .build();
    }

    private Behavior<Receptionist.Listing> onListing(Receptionist.Listing listing) {
        List<ActorRef<Bell.BellCommand>> availableBells = listing.getServiceInstances(bellServiceKey).stream().toList();
        bell = checkInstances(getContext(), availableBells, Bell.BellCommand.class);
        if(bell != null && gate == null){
            gate = getContext().spawn(Gate.create(PersistenceId.ofUniqueId(gateServiceKey.toString()), bell), String.format("Gate_with_key_%s", gateServiceKey));
            getContext().getSystem().receptionist().tell(Receptionist.register(gateServiceKey, gate));
            getContext().getLog().info("Controller registered with ServiceKey: {}",  gateServiceKey);
        }
        return Behaviors.same();
    }


}
