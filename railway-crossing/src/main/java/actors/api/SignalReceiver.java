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

  /**
   * Defines the type of commands that {@link SignalReceiver} can receive
   */
  public interface SignalReceiverCommand extends Command {}

  /**
   * This command is a response from a {@link Controller} to the message {@link Controller.CommandGetControllerName}
   * It contains the requested controllerId and the ActorRef of the {@link Controller}
   */
  public static class ControllerReply implements SignalReceiverCommand {

    public final String controllerId;

    private final ActorRef<Controller.ControllerCommand> sender;

    @JsonCreator
    public ControllerReply(
      @JsonProperty("controllerId") String controllerId,
      @JsonProperty("sender") ActorRef<Controller.ControllerCommand> sender
    ) {
      this.controllerId = controllerId;
      this.sender = sender;
    }
  }

  /**
   * This is an Adapter that contains a Messages of the {@link Receptionist.Listing} protocol
   * It is necessary to receive Messages of different protocols
   */
  public static class ListingWrapper implements SignalReceiverCommand {

    public final Receptionist.Listing listing;

    public ListingWrapper(Receptionist.Listing listing) {
      this.listing = listing;
    }
  }

  /**
   * This table contains all known {@link Controller} and stores for each controller
   * the controllerId and the ActorRef
   */
  private final HashMap<String, ActorRef<Controller.ControllerCommand>> controllerTable =
    new HashMap<>();

  /**
   * Creates a new {@link Behavior} for a  {@link SignalReceiver}
   * @return The new{@link Behavior} of the created {@link SignalReceiver}
   */
  public static Behavior<SignalReceiverCommand> create() {
    return Behaviors.setup(SignalReceiver::new);
  }

  /**
   * Initializes the {@link SignalReceiver}
   * @param context {@link ActorContext} of the current Actor-System, provides information and methods to
   *                                    interact with the Actor-System
   */
  private SignalReceiver(ActorContext<SignalReceiverCommand> context) {
    super(context);
    // Creates an ActorRef that can be used to receive messages from the Receptionist
    ActorRef<Receptionist.Listing> listingAdapter = context.messageAdapter(
      Receptionist.Listing.class,
      ListingWrapper::new
    );
    // Subscribe to the Receptionist to receive all ActorRefs that registered with the
    getContext()
      .getSystem()
      .receptionist()
      .tell(Receptionist.subscribe(ControllerSetup.universalKey, listingAdapter));

    getContext()
      .getLog()
      .info("SignalReceiver has been started and listening to universal Controller-Key");
    setupAPI();
  }

  /**
   * Defines the initial {@link Behavior} of the {@link SignalReceiver}:
   * - on messages of type {@link ListingWrapper} execute function onListing
   * - on messages of type {@link ControllerReply} execute function onControllerReply
   * @return the initial {@link Behavior}
   */
  @Override
  public Receive<SignalReceiverCommand> createReceive() {
    return newReceiveBuilder()
      .onMessage(ListingWrapper.class, this::onListing)
      .onMessage(ControllerReply.class, this::onControllerReply)
      .build();
  }

  /**
   * Defines how to handle messages of type {@link Receptionist.Listing} that is wrapped within {@link ListingWrapper}:
   * At the constructor the {@link SignalReceiver} informed the {@link Receptionist} to subscribe to the key {@link ControllerSetup#universalKey}.
   * So whenever an {@link Controller} is registered at the {@link Receptionist} with this key, the {@link Receptionist} will send
   * a message of type {@link Receptionist.Listing} to all {@link SignalReceiver} that are subscribed to the {@link ControllerSetup#universalKey}.
   * This message contains the {@link ActorRef} of each registered {@link Controller}.
   * This function handles the {@link Receptionist.Listing} message and updates the {@link SignalReceiver#controllerTable}.
   *
   * @param wrapper Message that contains the {@link Receptionist.Listing}
   * @return The same {@link Behavior} as before
   */
  private Behavior<SignalReceiverCommand> onListing(ListingWrapper wrapper) {
    //Extract the list of available ActorRefs
    List<ActorRef<Controller.ControllerCommand>> availableControllers = wrapper.listing
      .getServiceInstances(ControllerSetup.universalKey)
      .stream()
      .toList();
    //Remove those entries from the tabel that are no longer available
    List<String> remove = new ArrayList<>();
    controllerTable.forEach((id, ref) -> {
      if (!availableControllers.contains(ref)) {
        remove.add(id);
      }
    });
    remove.forEach(controllerTable::remove);
    //Request the controllerId from every available actor that is not in the table yet
    availableControllers.forEach(ref -> {
      if (!controllerTable.containsValue(ref)) {
        ref.tell(new Controller.CommandGetControllerName(getContext().getSelf()));
        getContext().getLog().info("Requested controllerId from {}", ref);
      }
    });
    return Behaviors.same();
  }

  /**
   * Defines how to handle messages of type {@link ControllerReply}:
   * In the function {@link SignalReceiver#onListing(ListingWrapper)} for every actor that is available but not in
   * the {@link SignalReceiver#controllerTable} yet, a message is sent to request the actors controllerId.
   * This function handles the responses of those actors by adding them to the table.
   *
   * @param reply The response from the actor containing the controllerId and {@link ActorRef}
   * @return The same {@link Behavior} as before
   */
  private Behavior<SignalReceiverCommand> onControllerReply(ControllerReply reply) {
    controllerTable.put(reply.controllerId, reply.sender);
    getContext()
      .getLog()
      .info("Received controllerId: {} from {}", reply.controllerId, reply.sender);
    return Behaviors.same();
  }

  /**
   * Creates the API for communication @see {@link SignalAPI}
   */
  private void setupAPI() {
    SignalAPI api = new SignalAPI(controllerTable);
    int httpPort = getContext()
      .getSystem()
      .settings()
      .config()
      .getInt("akka.http.server.default-http-port");

    Http.get(getContext().getSystem()).newServerAt("localhost", httpPort).bind(api.createRoutes());
    getContext()
      .getLog()
      .info("SignalReceiver API bound to {}:{}", getContext().getSystem().address(), httpPort);
  }
}
