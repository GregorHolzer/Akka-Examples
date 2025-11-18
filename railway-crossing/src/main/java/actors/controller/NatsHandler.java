package actors.controller;

import actors.Command;
import actors.NodeConfig;
import akka.actor.typed.ActorRef;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Receive;
import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import io.nats.client.Nats;

import java.io.IOException;

public class NatsHandler {

    public static interface NatsHandlerCommand extends Command {}

    private final ActorRef<Controller.ControllerCommand> controller;

    private final NodeConfig nodeConfig;

    private Connection nc;

    public NatsHandler(ActorRef<Controller.ControllerCommand> controller, NodeConfig nodeConfig) throws IOException, InterruptedException {
        this.controller = controller;
        this.nodeConfig = nodeConfig;
        Connection nc = Nats.connect("nats://" + nodeConfig.nats_server_addr() + ":" +  nodeConfig.nats_server_port());
        Dispatcher dispatcher = nc.createDispatcher(msg -> {

        });
    }
}
