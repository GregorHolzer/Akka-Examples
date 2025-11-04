package actors.api;

import actors.controller.Controller;
import akka.actor.typed.ActorRef;
import akka.http.javadsl.marshallers.jackson.Jackson;
import akka.http.javadsl.model.ContentTypes;

import akka.http.javadsl.model.HttpEntity;
import akka.http.javadsl.model.StatusCodes;
import akka.http.javadsl.server.AllDirectives;
import akka.http.javadsl.server.Route;

import java.util.HashMap;
import java.util.stream.Collectors;

import static akka.http.javadsl.server.PathMatchers.segment;

public class SignalAPI extends AllDirectives {

    private final HashMap<String, ActorRef<Controller.ControllerCommand>> controllerTable;


    public SignalAPI(HashMap<String, ActorRef<Controller.ControllerCommand>> controllerTable) {
        this.controllerTable = controllerTable;
    }

    public Route createRoutes() {
        return pathPrefix("railway-crossing", () -> concat(
                pathPrefix("controller", () -> concat(
                        path(segment().slash("trainSeen"), controllerId ->
                                post(() -> trainSeen(controllerId))
                        ),
                        path(segment().slash("trainNotSeen"), controllerId ->
                                post(() -> trainNotSeen(controllerId))
                        )
                )),
                pathPrefix("broadcast", () -> concat(
                        path("trainSeen", () ->
                                post(this::broadcastTrainSeen)
                        ),
                        path("trainNotSeen", () ->
                                post(this::broadcastTrainNotSeen)
                        )))));
    }


    private Route trainNotSeen(String controllerId) {
        if(!controllerTable.containsKey(controllerId)){
            return complete(StatusCodes.NOT_FOUND, controllerTable.keySet(), Jackson.marshaller());
        }
        controllerTable.get(controllerId).tell(new Controller.CommandTrainNotSeen());
        return complete(StatusCodes.OK);
    }

    private Route trainSeen(String controllerId) {
        if(!controllerTable.containsKey(controllerId)){
            return complete(StatusCodes.NOT_FOUND, controllerTable.keySet(), Jackson.marshaller());
        }
        controllerTable.get(controllerId).tell(new Controller.CommandTrainSeen());
        return complete(StatusCodes.OK);
    }

    private Route broadcastTrainSeen(){
        controllerTable.forEach((id, ref) -> {
            ref.tell(new Controller.CommandTrainSeen());
        });
        return complete(StatusCodes.OK);
    }

    private Route broadcastTrainNotSeen(){
        controllerTable.forEach((id, ref) -> {
            ref.tell(new Controller.CommandTrainNotSeen());
        });
        return complete(StatusCodes.OK);
    }
}
