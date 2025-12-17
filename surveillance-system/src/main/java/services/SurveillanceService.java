package services;

import actors.Surveillance;
import actors.common.SharedCommands;
import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.javadsl.ActorContext;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.model.StatusCodes;
import exchange.ContextVariableProtos;
import java.util.HashMap;
import java.util.concurrent.CompletionStage;

/**
 * Provides functionality to invoke Surveillance Services
 */
public class SurveillanceService implements AkkaService {

  /** Hostname of the cloud analysis service. */
  //TODO: read from configs
  private static final String host = "localhost";

  /** Port of the cloud analysis service. */
  //TODO: read from configs
  private static final int cloud_port = 8003;

  /**
   * Sends the image from a {@link Surveillance.FoundPersons} event
   * to the cloud analysis endpoint and handles the result.
   *
   * <p>If the request completes successfully and a variable named
   * {@code hasThreat} is found in the response, an
   * {@link Surveillance.Analyzed} message is sent back to the actor.
   * Otherwise, a {@link SharedCommands.InvocationFailure} is sent back.</p>
   *
   * @param context the surveillance actor context
   * @param foundPersons event containing the image to analyze
   */
  public void analyze(
    ActorContext<Surveillance.SurveillanceCommand> context,
    Surveillance.FoundPersons foundPersons
  ) {
    HashMap<String, Object> values = new HashMap<>();
    values.put("image", foundPersons.image());

    byte[] body = buildProtoRequestBody(values);

    ActorRef<Surveillance.SurveillanceCommand> self = context.getSelf();
    ActorSystem<?> system = context.getSystem();

    CompletionStage<HttpResponse> futureResponse = sendRequest(
      context,
      buildPostRequest(host, cloud_port, "/analyze", body)
    );

    futureResponse.whenComplete((response, throwableResponse) -> {
      if (throwableResponse == null && response.status().equals(StatusCodes.OK)) {
        extractContextVariable(system, response).whenComplete((var, throwableVar) -> {
          if (throwableVar == null) {
            ContextVariableProtos.ContextVariable variable = var
              .getDataList()
              .stream()
              .filter(v -> v.getName().equals("hasThreat"))
              .findFirst()
              .orElse(null);

            if (variable != null) {
              self.tell(
                new Surveillance.Analyzed(foundPersons.image(), variable.getValue().getBool())
              );
            } else {
              self.tell(new SharedCommands.InvocationFailure("/analyze: no hasThreat-field"));
            }
          } else {
            self.tell(new SharedCommands.InvocationFailure("/analyze: " + throwableVar));
          }
        });
      } else {
        self.tell(new SharedCommands.InvocationFailure("/analyze: " + throwableResponse));
      }
    });
  }
}
