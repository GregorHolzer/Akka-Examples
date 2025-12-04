package services;

import actors.Detector;
import akka.actor.typed.javadsl.ActorContext;
import akka.http.javadsl.model.StatusCodes;
import com.google.protobuf.InvalidProtocolBufferException;
import exchange.ContextVariableProtos;

import java.util.HashMap;

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
    sendRequest(context, buildPostRequest(host, iot_port, "/capture", body)).whenComplete((response, requestError) -> {
      if(requestError == null && response.status().equals(StatusCodes.OK)){
        context.getLog().info("Received Ok from /capture");
        response.entity().getDataBytes().runFold(akka.util.ByteString.emptyByteString(), akka.util.ByteString::concat, context.getSystem())
                .thenAccept(bytes -> {
                  try {
                    ContextVariableProtos.ContextVariables var = ContextVariableProtos.ContextVariables.parseFrom(bytes.toArray());
                    var.getDataList().stream().filter(data -> data.getName().equals("image"))
                            .forEach(data ->
                                    context.getSelf().tell(new Detector.CapturedImage(data.getValue().getBytes().toByteArray())));
                  } catch (InvalidProtocolBufferException e) {
                    context.getLog().error("Error parsing response: {}", e.getMessage());
                  }
                });
      }
      else{
        context.getLog().error("Received error from /capture");
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
    sendRequest(context, buildPostRequest(host, edge_port, "/detect", body)).whenComplete(
            (response, requestError) -> {
              if(requestError == null && response.status().equals(StatusCodes.OK)){
                extractContextVariable(context, response).whenComplete((var, parsingError) -> {
                  if(var != null &&  parsingError != null){
                    var.getDataList().stream().filter(data -> data.getName().equals("hasDetectedPersons"))
                            .forEach(data -> {
                              context.getSelf().tell(new Detector.DetectedPersons(capturedImage.image(), data
                                      .getValue().getBool()));
                            });
                  }
                });
              }
            }
    );
  }
}
