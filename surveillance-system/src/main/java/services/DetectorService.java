package services;

import actors.Detector;
import actors.common.GlobalCommands;
import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.javadsl.ActorContext;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.model.StatusCodes;
import exchange.ContextVariableProtos;

import java.util.HashMap;
import java.util.concurrent.CompletionStage;

public class DetectorService implements AkkaService{

  //TODO: maybe read host and port from config-file
  private static final String host = "localhost";

  private static final int iot_port = 8001;

  private static final Integer edge_port = 8002;

  private void sendRequestAndHandle(
          ActorContext<Detector.DetectorCommand> context,
          byte[] body,
          Integer port,
          String url,
          String variableName,
          java.util.function.BiConsumer<ActorRef<Detector.DetectorCommand>, ContextVariableProtos.ContextVariable> onSuccess
  ) {
    ActorRef<Detector.DetectorCommand> self = context.getSelf();
    ActorSystem<?> system = context.getSystem();
    CompletionStage<HttpResponse> futureResponse = sendRequest(context, buildPostRequest(host, port, url, body));
    futureResponse.whenComplete((response, throwableResponse) -> {
      if (throwableResponse == null && response.status().equals(StatusCodes.OK)) {
        extractContextVariable(system, response).whenComplete((var, throwableVar) -> {
          if (throwableVar == null) {
            ContextVariableProtos.ContextVariable variable = var.getDataList().stream()
                    .filter(v -> v.getName().equals(variableName))
                    .findFirst()
                    .orElse(null);
            if (variable != null) {
              onSuccess.accept(self, variable);
            } else {
              self.tell(new GlobalCommands.InvocationFailure(url + ": no " + variableName + "-field"));
            }
          } else {
            self.tell(new GlobalCommands.InvocationFailure(url + ": " + throwableVar));
          }
        });
      } else {
        self.tell(new GlobalCommands.InvocationFailure(url + ": " + throwableResponse));
      }
    });
  }

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
    sendRequestAndHandle(
            context,
            body,
            iot_port,
            "/capture",
            "image",
            (self, imageVar) ->
                    self.tell(new Detector.CapturedImage(imageVar.getValue().getBytes().toByteArray()))
    );
  }

  public void detectPersons(
    ActorContext<Detector.DetectorCommand> context,
    Detector.CapturedImage capturedImage
  ) {
    HashMap<String, Object>  values = new HashMap<>();
    values.put("image", capturedImage.image());
    byte[] body = buildProtoRequestBody(values);
    sendRequestAndHandle(
            context,
            body,
            edge_port,
            "/detect",
            "hasDetectedPersons",
            (self, detectedVar) ->
                    self.tell(new Detector.DetectedPersons(capturedImage.image(), detectedVar.getValue().getBool()))
    );
  }
}
