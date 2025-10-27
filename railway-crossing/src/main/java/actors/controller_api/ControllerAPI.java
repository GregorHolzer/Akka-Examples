package actors.controller_api;

import actors.controller.Controller;
import akka.actor.typed.ActorRef;
import akka.http.javadsl.model.StatusCodes;
import akka.http.javadsl.server.AllDirectives;
import akka.http.javadsl.server.Route;

import static akka.http.javadsl.server.PathMatchers.segment;

public class ControllerAPI extends AllDirectives {

    private final ActorRef<Controller.ControllerCommand> controller;

    public ControllerAPI(ActorRef<Controller.ControllerCommand> controller) {
        this.controller = controller;
    }

    public Route createRoutes() {
        return pathPrefix("railway-crossing", () -> concat(
                pathPrefix("controller", () -> concat(
                                path("trainSeen", () -> post(this::trainSeen)),
                                path("trainNotSeen", () ->  post(this::trainNotSeen))
                        ))));
    }

    private Route trainNotSeen() {
        controller.tell(new Controller.CommandTrainNotSeen());
        return complete(StatusCodes.OK);
    }

    private Route trainSeen() {
        controller.tell(new Controller.CommandTrainSeen());
        return complete(StatusCodes.OK);
    }
}
