package services;

import actors.Detector;
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
 * Provides functionality to invoke Detector Services
 */
public class DetectorService implements AkkaService {

  /** Hostname for IoT and edge services. */
  //TODO: read from configs
  private static final String host = "localhost";

  /** Port used by the IoT service. */
  //TODO: read from configs
  private static final int iot_port = 8001;

  /** Port used by the edge detection service. */
  //TODO: read from configs
  private static final Integer edge_port = 8002;

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
   * @param variableName the expected variable name in the response
   * @param onSuccess callback executed on successful extraction
   */
  private void sendRequestAndHandle(
    ActorContext<Detector.DetectorCommand> context,
    byte[] body,
    Integer port,
    String url,
    String variableName,
    java.util.function.BiConsumer<
      ActorRef<Detector.DetectorCommand>,
      ContextVariableProtos.ContextVariable
    > onSuccess
  ) {
    ActorRef<Detector.DetectorCommand> self = context.getSelf();
    ActorSystem<?> system = context.getSystem();

    CompletionStage<HttpResponse> futureResponse = sendRequest(
      context,
      buildPostRequest(host, port, url, body)
    );

    futureResponse.whenComplete((response, throwableResponse) -> {
      if (throwableResponse == null && response.status().equals(StatusCodes.OK)) {
        extractContextVariable(system, response).whenComplete((var, throwableVar) -> {
          if (throwableVar == null) {
            ContextVariableProtos.ContextVariable variable = var
              .getDataList()
              .stream()
              .filter(v -> v.getName().equals(variableName))
              .findFirst()
              .orElse(null);

            if (variable != null) {
              onSuccess.accept(self, variable);
            } else {
              self.tell(
                new SharedCommands.InvocationFailure(url + ": no " + variableName + "-field")
              );
            }
          } else {
            self.tell(new SharedCommands.InvocationFailure(url + ": " + throwableVar));
          }
        });
      } else {
        self.tell(new SharedCommands.InvocationFailure(url + ": " + throwableResponse));
      }
    });
  }

  /**
   * Sends a request to activate the alarm via the IoT service.
   *
   * @param context the detector actor context
   */
  public void alarmOn(ActorContext<Detector.DetectorCommand> context) {
    sendRequest(context, buildPostRequest(host, iot_port, "/alarm/on"));
  }

  /**
   * Sends a request to deactivate the alarm via the IoT service.
   *
   * @param context the detector actor context
   */
  public void alarmOff(ActorContext<Detector.DetectorCommand> context) {
    sendRequest(context, buildPostRequest(host, iot_port, "/alarm/off"));
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
  public void cameraCapture(ActorContext<Detector.DetectorCommand> context, Integer cameraId) {
    HashMap<String, Object> values = new HashMap<>();
    values.put("cameraId", cameraId);

    byte[] body = buildProtoRequestBody(values);

    sendRequestAndHandle(context, body, iot_port, "/capture", "image", (self, imageVar) ->
      self.tell(new Detector.CapturedImage(imageVar.getValue().getBytes().toByteArray()))
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

    byte[] body = buildProtoRequestBody(values);

    sendRequestAndHandle(
      context,
      body,
      edge_port,
      "/detect",
      "hasDetectedPersons",
      (self, detectedVar) ->
        self.tell(
          new Detector.DetectedPersons(capturedImage.image(), detectedVar.getValue().getBool())
        )
    );
  }
}
