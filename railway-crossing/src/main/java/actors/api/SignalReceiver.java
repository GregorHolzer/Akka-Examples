package actors.api;

import actors.Command;
import actors.controller.Controller;
import actors.setup.ControllerSetup;
import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import akka.actor.typed.receptionist.Receptionist;
import akka.http.javadsl.Http;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;


public class SignalReceiver extends AbstractBehavior<SignalReceiver.SignalReceiverCommand> {

    public interface SignalReceiverCommand extends Command {}

    public static class ControllerReply implements SignalReceiverCommand{

        public final String controllerId;

        private final ActorRef<Controller.ControllerCommand> sender;

        @JsonCreator
        public ControllerReply(@JsonProperty("controllerId") String controllerId,
                               @JsonProperty("sender") ActorRef<Controller.ControllerCommand> sender){
            this.controllerId = controllerId;
            this.sender = sender;
        }
    }

    public static class ListingWrapper implements SignalReceiverCommand{
        public final Receptionist.Listing listing;

        public ListingWrapper(Receptionist.Listing listing){
            this.listing = listing;
        }
    }

    private final HashMap<String, ActorRef<Controller.ControllerCommand>> controllerTable = new HashMap<>();

    public static Behavior<SignalReceiverCommand> create() {
        return Behaviors.setup(SignalReceiver::new);
    }

    private SignalReceiver(ActorContext<SignalReceiverCommand> context) {
        super(context);
        ActorRef<Receptionist.Listing> listingAdapter =
                context.messageAdapter(Receptionist.Listing.class, ListingWrapper::new);
        getContext().getSystem().receptionist().tell(Receptionist.subscribe(ControllerSetup.universalKey, listingAdapter));
        getContext().getLog().info("SignalReceiver has been started and listening to universal Controller-Key");
        setupAPI();
    }

    @Override
    public Receive<SignalReceiverCommand> createReceive() {
        return newReceiveBuilder()
                .onMessage(ListingWrapper.class, this::onListing)
                .onMessage(ControllerReply.class, this::onControllerReply)
                .build();
    }

    private Behavior<SignalReceiverCommand> onListing(ListingWrapper wrapper){
        List<ActorRef<Controller.ControllerCommand>> availableControllers = wrapper.listing.getServiceInstances(ControllerSetup.universalKey).stream().toList();

        List<String> remove = new ArrayList<>();
        controllerTable.forEach((id, ref) -> {
            if(!availableControllers.contains(ref)){
                remove.add(id);
            }
        });

        remove.forEach(controllerTable::remove);

        availableControllers.forEach(ref -> {
            if(!controllerTable.containsValue(ref)){
                ref.tell(new Controller.CommandGetCrossingID(getContext().getSelf()));
                getContext().getLog().info("Requested controllerId from {}", ref);
            }
        });

        return Behaviors.same();
    }

    private Behavior<SignalReceiverCommand> onControllerReply(ControllerReply reply){
        controllerTable.put(reply.controllerId, reply.sender);
        getContext().getLog().info("Received controllerId: {} from {}", reply.controllerId, reply.sender);
        return Behaviors.same();
    }

    private void setupAPI(){
        SignalAPI api = new SignalAPI(controllerTable);
        int httpPort = getContext().getSystem().settings()
                .config()
                .getInt("akka.http.server.default-http-port");

        Http.get(getContext().getSystem())
                .newServerAt("localhost", httpPort)
                .bind(api.createRoutes());
        getContext().getLog().info("SignalReceiver API bound to {}:{}",getContext().getSystem().address(), httpPort);
    }
}