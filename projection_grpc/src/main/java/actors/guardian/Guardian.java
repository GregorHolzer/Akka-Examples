package actors.guardian;

import actors.Command;
import actors.ComponentType;
import actors.receiver.EventConsumer;
import actors.receiver.Receiver;
import actors.sender.EventPublisher;
import actors.sender.Sender;
import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import akka.http.javadsl.Http;
import akka.http.javadsl.ServerBinding;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.server.Route;
import akka.persistence.typed.PersistenceId;
import akka.http.javadsl.model.StatusCodes;

import static akka.http.javadsl.server.Directives.*;
import static akka.http.javadsl.server.PathMatchers.segment;

import java.net.InetSocketAddress;
import java.util.concurrent.CompletionStage;
import akka.japi.function.Function;


public class Guardian extends AbstractBehavior<Command> {

    public static Behavior<Command> create() {
        return Behaviors.setup(Guardian::new);
    }

    private Guardian(ActorContext<Command> context) {
        super(context);
        setupControllers();
    }

    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder().build();
    }

    private void setupControllers(){
        ComponentType type = ComponentType.valueOf(System.getenv("TYPE"));
        switch (type) {
            case Receiver -> {
                ActorRef<Receiver.ReceiverCommand> receiver =  getContext().spawn(Receiver.create(PersistenceId.ofUniqueId("Receiver")), "Receiver");
                var projection = EventConsumer.init(getContext().getSystem(), receiver);
                getContext().spawn(projection, "Projection");
                getContext().getLog().info("Receiver has been initialized");
            }
            case Sender -> {
                ActorRef<Sender.SenderCommand> sender = getContext().spawn(Sender.create(PersistenceId.of("Sender", "1")), "Sender");
                int httpPort = 8000;
                Http.get(getContext().getSystem()).newServerAt("localhost", httpPort).bind(senderAPI(sender));
                getContext()
                        .getLog()
                        .info("SignalReceiver API bound to {}:{}", getContext().getSystem().address(), httpPort);
                Function<HttpRequest, CompletionStage<HttpResponse>> eventProducerService = EventPublisher.eventProducerService(getContext().getSystem());
                CompletionStage<ServerBinding> bound =
                        Http.get(getContext().getSystem()).newServerAt("0.0.0.0", 8080).bind(eventProducerService);

                bound.whenComplete((binding, ex) -> {
                    if (binding != null) {
                        InetSocketAddress address = binding.localAddress();
                        getContext().getSystem().log().info(
                                "gRPC server online at {}:{}",
                                address.getHostString(),
                                address.getPort());
                    } else {
                        getContext().getSystem().log().error("Failed to bind gRPC endpoint, terminating system", ex);
                        getContext().getSystem().terminate();
                    }
                });
            }
        }
    }

    private Route senderAPI(ActorRef<Sender.SenderCommand> sender) {
        return pathPrefix("railway-crossing", () ->
                concat(post(() -> sendMsg(sender))));
    }

    private Route sendMsg(ActorRef<Sender.SenderCommand> sender) {
        sender.tell(new Sender.SendMessage());
        return complete(StatusCodes.OK);
    }
}
