package services;

import actors.Detector;
import actors.GlobalCommands;
import akka.actor.typed.javadsl.ActorContext;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.model.StatusCodes;
import exchange.ContextVariableProtos;

import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CompletionStage;

public class DetectorService implements AkkaService{

  //TODO: maybe read host and port from config-file
  private static final String host = "localhost";

  private static final int iot_port = 8001;

  private static final Integer edge_port = 8002;

  public void alarmOn(ActorContext<Detector.DetectorCommand> context) {
      sendRequest(context, buildPostRequest(host, iot_port, "/alarm/on"));
  }

  public void alarmOff(ActorContext<Detector.DetectorCommand> context) {
     sendRequest(context, buildPostRequest(host, iot_port, "/alarm/off"));
  }

  public void cameraCapture(ActorContext<Detector.DetectorCommand> context, Integer cameraId) {
    HashMap<String, Object>  values = new HashMap<>();
    values.put("cameraId", cameraId);
    byte[] body = buildProtoRequestBody(values);
    CompletionStage<HttpResponse> futureResponse = sendRequest(context, buildPostRequest(host, iot_port, "/capture", body));
    futureResponse.whenComplete((response, throwable) -> {
      if (throwable == null && response.status() == StatusCodes.OK) {
        CompletionStage<ContextVariableProtos.ContextVariables> futureVar = extractContextVariable(context, response);
        context.pipeToSelf(futureVar, (contextVar, throwable1) -> {
          if(throwable1 == null){
            List<ContextVariableProtos.ContextVariable> variables = contextVar.getDataList();
            ContextVariableProtos.ContextVariable image = variables.stream().filter(v -> v.getName().equals("image")).findFirst().orElse(null);
            if(image != null){
              return new Detector.CapturedImage(image.getValue().getBytes().toByteArray());
            }
          }
          return new GlobalCommands.InvocationFailure("cameraCapture");
        });
      }
    });
  }

  public void detectPersons(
    ActorContext<Detector.DetectorCommand> context,
    Detector.CapturedImage capturedImage
  ) {
    HashMap<String, Object>  values = new HashMap<>();
    values.put("image", capturedImage.image());
    byte[] body = buildProtoRequestBody(values);
    CompletionStage<HttpResponse> futureResponse = sendRequest(context, buildPostRequest(host, edge_port, "/detect", body));
    futureResponse.whenComplete((response, throwable) -> {
      if(throwable == null && response.status() == StatusCodes.OK) {
        CompletionStage<ContextVariableProtos.ContextVariables> futureVar = extractContextVariable(context, response);
        context.pipeToSelf(futureVar, (contextVar, throwable1) -> {
          if(throwable1 == null){
            List<ContextVariableProtos.ContextVariable> variables = contextVar.getDataList();
            ContextVariableProtos.ContextVariable hasDetectedPersons = variables.stream().filter(v -> v.getName().equals("hasDetectedPersons")).findFirst().orElse(null);
            if(hasDetectedPersons != null){
              return new Detector.DetectedPersons(capturedImage.image(), hasDetectedPersons.getValue().getBool());
            }
          }
          return new GlobalCommands.InvocationFailure("detectPersons");
        });
      }
    });
  }
}
