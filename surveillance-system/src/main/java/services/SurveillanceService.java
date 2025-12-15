package services;


import actors.Detector;
import actors.common.GlobalCommands;
import actors.Surveillance;
import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.javadsl.ActorContext;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.model.StatusCodes;
import exchange.ContextVariableProtos;

import java.util.HashMap;
import java.util.concurrent.CompletionStage;

public class SurveillanceService implements AkkaService {

  //TODO: maybe read host and port from config-file
  private static final String host = "localhost";

  private static final int cloud_port = 8003;

  public void analyze(
    ActorContext<Surveillance.SurveillanceCommand> context,
    Surveillance.FoundPersons foundPersons
  ) {
    HashMap<String, Object> values = new HashMap<>();
    values.put("image", foundPersons.image());
    byte[] body = buildProtoRequestBody(values);
    ActorRef<Surveillance.SurveillanceCommand> self = context.getSelf();
    ActorSystem<?> system = context.getSystem();
    CompletionStage<HttpResponse> futureResponse = sendRequest(context, buildPostRequest(host, cloud_port, "/analyze", body));

    futureResponse.whenComplete((response, throwableResponse) -> {
      if (throwableResponse == null && response.status().equals(StatusCodes.OK)) {
        extractContextVariable(system, response).whenComplete((var, throwableVar) -> {
          if (throwableVar == null) {
            ContextVariableProtos.ContextVariable variable = var.getDataList().stream()
                    .filter(v -> v.getName().equals("hasThreat"))
                    .findFirst()
                    .orElse(null);
            if (variable != null) {
              self.tell(new Surveillance.Analyzed(foundPersons.image(), variable.getValue().getBool()));
            } else {
              self.tell(new GlobalCommands.InvocationFailure("/analyze: no hasThreat-field"));
            }
          } else {
            self.tell(new GlobalCommands.InvocationFailure("/analyze: " + throwableVar));
          }
        });
      } else {
        self.tell(new GlobalCommands.InvocationFailure("/analyze: " + throwableResponse));
      }
    });
  }
}
