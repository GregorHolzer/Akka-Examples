package services;

import actors.Detector;
import actors.common.Configuration;
import actors.common.SharedCommands;
import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.javadsl.ActorContext;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.model.StatusCodes;
import exchange.ContextVariableProtos;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CompletionStage;

/**
 * Provides functionality to invoke Detector Services
 */
public class DetectorService implements AkkaService {

  /** Hostname for IoT services. */
  private final String iot_addr;

  /** Hostname for Edge services. */
  private final String edge_addr;

  /** Port used by the IoT service. */
  private final int iot_port;

  /** Port used by the edge detection service. */
  private final Integer edge_port;

  public DetectorService() {
    Configuration.NodeConfiguration config =
      Configuration.getNodeConfiguration();
    iot_addr = config.iot_service_addr();
    iot_port = config.iot_service_port();
    edge_addr = config.edge_service_addr();
    edge_port = config.edge_service_port();
  }

  /**
   * Sends an HTTP request with a Protobuf body and handles the response.
   *
   * <p>If the request succeeds and the expected variable is found in the
   * response, the provided success handler is invoked. Otherwise, an
   * {@link SharedCommands.InvocationFailure} message is sent to the calling actor.</p>
   *
   * @param context the detector actor context
   * @param body the Protobuf request body
   * @param port the target service port
   * @param url the request path
   * @param variableNames the expected variable name in the response
   * @param onSuccess callback executed on successful extraction
   */
  private void sendRequestAndHandle(
    ActorContext<Detector.DetectorCommand> context,
    byte[] body,
    String host,
    Integer port,
    String url,
    List<String> variableNames,
    java.util.function.BiConsumer<
      ActorRef<Detector.DetectorCommand>,
      List<ContextVariableProtos.ContextVariable>
    > onSuccess
  ) {
    ActorRef<Detector.DetectorCommand> self = context.getSelf();
    ActorSystem<?> system = context.getSystem();

    CompletionStage<HttpResponse> futureResponse = sendRequest(
      context,
      buildPostRequest(host, port, url, body)
    );

    futureResponse.whenComplete((response, throwableResponse) -> {
      if (
        throwableResponse == null && response.status().equals(StatusCodes.OK)
      ) {
        extractContextVariable(system, response).whenComplete(
          (var, throwableVar) -> {
            if (throwableVar == null) {
              List<ContextVariableProtos.ContextVariable> variables = var
                .getDataList()
                .stream()
                .filter(v -> variableNames.contains(v.getName())).toList();

              if (variables.size() == variableNames.size()) {
                onSuccess.accept(self, variables);
              } else {
                self.tell(
                  new SharedCommands.InvocationFailure(
                    url + ": missing fields: expected " + variableNames
                  )
                );
              }
            } else {
              self.tell(
                new SharedCommands.InvocationFailure(url + ": " + throwableVar)
              );
            }
          }
        );
      } else {
        if (response != null) {
          response.discardEntityBytes(system);
        }
        self.tell(
          new SharedCommands.InvocationFailure(url + ": " + throwableResponse)
        );
      }
    });
  }

  /**
   * Sends a request to activate the alarm via the IoT service.
   *
   * @param context the detector actor context
   */
  public void alarmOn(ActorContext<Detector.DetectorCommand> context, SharedCommands.Alarm alarm) {
    HashMap<String, Object> values = new HashMap<>();
    values.put("traceId", alarm.traceId());
    values.put("spanId", alarm.spanId());

    byte[] body = buildProtoRequestBody(values);

    sendRequest(context, buildPostRequest(iot_addr, iot_port, "/alarm/on", body));
  }

  /**
   * Sends a request to deactivate the alarm via the IoT service.
   *
   * @param context the detector actor context
   */
  public void alarmOff(ActorContext<Detector.DetectorCommand> context) {
    sendRequest(context, buildPostRequest(iot_addr, iot_port, "/alarm/off"));
  }

  /**
   * Triggers a camera capture on the IoT service.
   *
   * <p>The captured image is returned asynchronously and sent back
   * to the detector actor as a {@link Detector.CapturedImage} message.</p>
   *
   * @param context the detector actor context
   * @param cameraId the camera identifier
   */
  public void cameraCapture(
    ActorContext<Detector.DetectorCommand> context,
    Integer cameraId
  ) {
    HashMap<String, Object> values = new HashMap<>();
    values.put("cameraId", cameraId);

    byte[] body = buildProtoRequestBody(values);

    sendRequestAndHandle(
      context,
      body,
      iot_addr,
      iot_port,
      "/capture",
      List.of("image", "traceId", "spanId"),
      (self, variables) ->
        self.tell(
          new Detector.CapturedImage(
                  variables.getFirst().getValue().getBytes().toByteArray(),
                  variables.get(1).getValue().getString(),
                  variables.get(2).getValue().getString()
          )
        )
    );
  }

  /**
   * Sends an image to the edge service for person detection.
   *
   * <p>The detection result is returned to the detector actor as a
   * {@link Detector.DetectedPersons} message.</p>
   *
   * @param context the detector actor context
   * @param capturedImage the previously captured image
   */
  public void detectPersons(
    ActorContext<Detector.DetectorCommand> context,
    Detector.CapturedImage capturedImage
  ) {
    HashMap<String, Object> values = new HashMap<>();
    values.put("image", capturedImage.image());
    values.put("traceId",  capturedImage.traceId());
    values.put("spanId", capturedImage.spanId());

    byte[] body = buildProtoRequestBody(values);

    sendRequestAndHandle(
      context,
      body,
      edge_addr,
      edge_port,
      "/detect",
      List.of("hasDetectedPersons", "traceId", "spanId"),
      (self, list) ->
        self.tell(
          new Detector.DetectedPersons(
            capturedImage.image(),
            list.getFirst().getValue().getBool(),
            list.get(1).getValue().getString(),
            list.get(2).getValue().getString()
          )
        )
    );
  }
}
