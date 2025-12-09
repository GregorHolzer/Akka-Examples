package services;


import actors.GlobalCommands;
import actors.Surveillance;
import akka.actor.typed.javadsl.ActorContext;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.model.StatusCodes;
import exchange.ContextVariableProtos;

import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CompletionStage;

//Dummy Services
public class SurveillanceService implements AkkaService {

  //TODO: maybe read host and port from config-file
  private static final String host = "localhost";

  private static final int cloud_port = 8003;

  public void analyze(
    ActorContext<Surveillance.SurveillanceCommand> context,
    Surveillance.FoundPersons foundPersons
  ) {
    HashMap<String, Object> values = new HashMap<>();
    values.put("image", foundPersons.image);
    byte[] body = buildProtoRequestBody(values);
    CompletionStage<HttpResponse> futureResponse = sendRequest(context, buildPostRequest(host, cloud_port, "/analyze", body));
    futureResponse.whenComplete((response, throwable) -> {
      if (throwable == null && response.status() == StatusCodes.OK) {
        CompletionStage<ContextVariableProtos.ContextVariables> futureVar = extractContextVariable(context, response);
        context.pipeToSelf(futureVar, (contextVar, throwable1) -> {
          if(throwable1 == null){
            List<ContextVariableProtos.ContextVariable> variables = contextVar.getDataList();
            ContextVariableProtos.ContextVariable hasThreat = variables.stream().filter(v -> v.getName().equals("hasThreat")).findFirst().orElse(null);
            if(hasThreat != null){
              return new Surveillance.Analyzed(foundPersons.image, hasThreat.getValue().getBool());
            }
          }
          return new GlobalCommands.InvocationFailure("analyze");
        });
      }
      });
  }
}
