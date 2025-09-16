package api;


import actors.Controller;
import akka.actor.typed.ActorRef;
import akka.cluster.sharding.typed.ShardingEnvelope;
import akka.http.javadsl.model.StatusCodes;
import akka.http.javadsl.server.AllDirectives;
import akka.http.javadsl.server.Route;
import java.time.Duration;
import static akka.http.javadsl.server.PathMatchers.segment;


/// API to send {@link actors.messages.ControllerMessage} to {@link actors.Controller} that are identified by an id
/// and to receive current state of the RailWayCrossing-Cluster
public class RailWayCrossingAPI extends AllDirectives {

    private final ActorRef<ShardingEnvelope<Controller.SensorMessage>> controller;

    public RailWayCrossingAPI(ActorRef<ShardingEnvelope<Controller.SensorMessage>> controller) {
        this.controller = controller;
    }

    public Route createRoutes() {
        return pathPrefix("railway-crossing", () -> concat(
                pathPrefix("controller", () ->
                        pathPrefix(segment(), name -> concat(
                                path("trainSeen", () -> post(() -> trainSeen(name))),
                                path("trainNotSeen", () ->  post(() -> trainNotSeen(name)))
                        )))));
    }

    private Route trainSeen(String name){
        controller.tell(new ShardingEnvelope<>(name, new Controller.TrainSeen()));
        return complete(StatusCodes.OK);
    }

    private Route trainNotSeen(String name){
        controller.tell(new ShardingEnvelope<>(name, new Controller.TrainNotSeen()));
        return complete(StatusCodes.OK);
    }
}
