package actors.api;

import static akka.http.javadsl.server.PathMatchers.segment;

import actors.controller.Controller;
import akka.actor.typed.ActorRef;
import akka.http.javadsl.marshallers.jackson.Jackson;
import akka.http.javadsl.model.StatusCodes;
import akka.http.javadsl.server.AllDirectives;
import akka.http.javadsl.server.Route;
import java.util.HashMap;

public class SignalAPI extends AllDirectives {

  /**
   * HashTable that contains all known pairs of controllerIds, and ActorRefs
   */
  private final HashMap<String, ActorRef<Controller.ControllerCommand>> controllerTable;

  /**
   * Initializes this Signal-API object that provides an API to send Messages to {@link Controller}
   * Type of messages that can be sent:
   *    - {@link Controller.CommandTrainSeen}
   *    - {@link Controller.CommandTrainNotSeen}
   *
   * @param controllerTable     Table of known {@link Controller}
   */
  public SignalAPI(HashMap<String, ActorRef<Controller.ControllerCommand>> controllerTable) {
    this.controllerTable = controllerTable;
  }

  /**
   * Creates the routes for the API, available routes are:
   *    - <host><port>/railway-crossing/controller/<controllerId>/trainSeen
   *    - <host><port>/railway-crossing/controller/<controllerId>/trainNotSeen
   *    - <host><port>/railway-crossing/broadcast/trainSeen
   *    - <host><port>/railway-crossing/broadcast/trainNotSeen
   *
   * @return Route-Object
   */
  public Route createRoutes() {
    return pathPrefix("railway-crossing", () ->
      concat(
        pathPrefix("controller", () ->
          concat(
            path(segment().slash("trainSeen"), controllerId -> post(() -> trainSeen(controllerId))),
            path(segment().slash("trainNotSeen"), controllerId ->
              post(() -> trainNotSeen(controllerId))
            )
          )
        ),
        pathPrefix("broadcast", () ->
          concat(
            path("trainSeen", () -> post(this::broadcastTrainSeen)),
            path("trainNotSeen", () -> post(this::broadcastTrainNotSeen))
          )
        )
      )
    );
  }

  private Route trainNotSeen(String controllerId) {
    if (!controllerTable.containsKey(controllerId)) {
      return complete(StatusCodes.NOT_FOUND, controllerTable.keySet(), Jackson.marshaller());
    }
    controllerTable.get(controllerId).tell(new Controller.CommandTrainNotSeen());
    return complete(StatusCodes.OK);
  }

  private Route trainSeen(String controllerId) {
    if (!controllerTable.containsKey(controllerId)) {
      return complete(StatusCodes.NOT_FOUND, controllerTable.keySet(), Jackson.marshaller());
    }
    controllerTable.get(controllerId).tell(new Controller.CommandTrainSeen());
    return complete(StatusCodes.OK);
  }

  private Route broadcastTrainSeen() {
    controllerTable.forEach((id, ref) -> {
      ref.tell(new Controller.CommandTrainSeen());
    });
    return complete(StatusCodes.OK);
  }

  private Route broadcastTrainNotSeen() {
    controllerTable.forEach((id, ref) -> {
      ref.tell(new Controller.CommandTrainNotSeen());
    });
    return complete(StatusCodes.OK);
  }
}
