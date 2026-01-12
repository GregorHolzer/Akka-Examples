package actors.common;

import actors.Bell;
import akka.actor.typed.ActorRef;
import akka.actor.typed.javadsl.ActorContext;
import akka.http.javadsl.Http;
import akka.http.javadsl.model.*;
import akka.util.ByteString;
import exchange.ContextVariableProtos.*;
import java.util.concurrent.CompletionStage;

/**
 * Provides functionality to invoke the Railway Service via HTTP.
 *
 * <p>This class builds and sends HTTP requests to the configured
 * Railway Service endpoints using Akka HTTP.</p>
 */
public class RailwayService {

  private final Configuration.NodeConfiguration nodeConfig;

  /**
   * Creates a new {@code RailwayService} using the node configuration.
   */
  public RailwayService() {
    this.nodeConfig = Configuration.getNodeConfiguration();
  }

  /**
   * Builds the service URL based on the node configuration and the given path.
   *
   * @param path the path to the requested resource.
   * @return the full service URL constructed from the configuration and path.
   */
  private String getUrl(String path) {
    return (
      "http://" +
      nodeConfig.service_server_addr() +
      ":" +
      nodeConfig.service_server_port() +
      path
    );
  }

  /**
   * Sends an HTTP request to the Railway Service.
   *
   * @param context the actor context used to access the actor system.
   * @param request the HTTP request to send.
   * @return a {@link CompletionStage} containing the HTTP response.
   */
  private CompletionStage<HttpResponse> sendRequest(
    ActorContext<?> context,
    HttpRequest request
  ) {
    return Http.get(context.getSystem()).singleRequest(request);
  }

  /**
   * Builds an HTTP POST request without a request body.
   *
   * @param path       the path to the requested resource.
   * @param crossingId the railway crossing ID of the calling component.
   * @return the constructed {@link HttpRequest}.
   */
  private HttpRequest buildRequest(String path, String crossingId) {
    String url = getUrl(path);
    return HttpRequest.POST(url).addHeader(
      HttpHeader.parse("Cirrina-Sender-ID", crossingId)
    );
  }

  /**
   * Builds an HTTP POST request with a binary request body.
   *
   * @param path       the path to the requested resource.
   * @param crossingId the railway crossing ID of the calling component.
   * @param body       the request body payload.
   * @return the constructed {@link HttpRequest}.
   */
  private HttpRequest buildRequest(
    String path,
    String crossingId,
    byte[] body
  ) {
    String url = getUrl(path);
    return HttpRequest.POST(url)
      .addHeader(HttpHeader.parse("Cirrina-Sender-ID", crossingId))
      .withEntity(
        HttpEntities.create(
          ContentTypes.APPLICATION_OCTET_STREAM,
          ByteString.fromArray(body)
        )
      );
  }

  /**
   * Builds a request body containing the approaching train speed.
   *
   * @param trainSpeed the approaching train speed.
   * @return the serialized request body.
   */
  private byte[] buildRequestBody(Double trainSpeed) {
    ContextVariables.Builder var = ContextVariables.newBuilder();
    var.addData(
      ContextVariable.newBuilder()
        .setName("approachingSpeed")
        .setValue(Value.newBuilder().setDouble(trainSpeed).build())
        .build()
    );
    return var.build().toByteArray();
  }

  /**
   * Builds a request body containing tracing information.
   *
   * @param traceId the trace identifier.
   * @param spanId  the span identifier.
   * @return the serialized request body.
   */
  private byte[] buildRequestBody(String traceId, String spanId) {
    ContextVariables.Builder var = ContextVariables.newBuilder();
    var.addData(
      ContextVariable.newBuilder()
        .setName("traceId")
        .setValue(Value.newBuilder().setString(traceId).build())
    );
    var.addData(
      ContextVariable.newBuilder()
        .setName("spanId")
        .setValue(Value.newBuilder().setString(spanId).build())
    );
    return var.build().toByteArray();
  }

  /**
   * Turns the bell on.
   *
   * @param context    the {@link ActorContext} of the calling actor.
   * @param crossingId the railway crossing ID of the bell.
   * @param trainSpeed the train speed.
   */
  public void bellOn(
    ActorContext<?> context,
    String crossingId,
    Double trainSpeed
  ) {
    byte[] body = buildRequestBody(trainSpeed);
    HttpRequest request = buildRequest("/bell/on", crossingId, body);
    sendRequest(context, request);
  }

  /**
   * Turns the bell off.
   *
   * @param context    the {@link ActorContext} of the calling actor.
   * @param crossingId the railway crossing ID of the bell.
   * @param traceId    the trace identifier.
   * @param spanId     the span identifier.
   */
  public void bellOff(
    ActorContext<?> context,
    String crossingId,
    String traceId,
    String spanId
  ) {
    byte[] body = buildRequestBody(traceId, spanId);
    HttpRequest request = buildRequest("/bell/off", crossingId, body);
    sendRequest(context, request);
  }

  /**
   * Opens the gate and sends a {@link Bell.CommandBellOff} message to the Bell actor after successful invocation.
   *
   * @param context    the {@link ActorContext} of the calling actor.
   * @param bell       reference to the bell actor.
   * @param crossingId the railway crossing ID of the gate.
   * @param traceId    the trace identifier.
   * @param spanId     the span identifier.
   */
  public void gateUp(
    ActorContext<?> context,
    ActorRef<Bell.BellCommand> bell,
    String crossingId,
    String traceId,
    String spanId
  ) {
    HttpRequest request = buildRequest("/gate/up", crossingId);
    sendRequest(context, request).whenComplete((httpResponse, throwable) -> {
      if (throwable == null && httpResponse.status().equals(StatusCodes.OK)) {
        bell.tell(new Bell.CommandBellOff(traceId, spanId));
      }
    });
  }

  /**
   * Closes the gate.
   *
   * @param context    the {@link ActorContext} of the calling actor.
   * @param crossingId the railway crossing ID of the gate.
   * @param trainSpeed the approaching train speed
   */
  public void gateDown(
    ActorContext<?> context,
    String crossingId,
    Double trainSpeed
  ) {
    byte[] body = buildRequestBody(trainSpeed);
    HttpRequest request = buildRequest("/gate/down", crossingId, body);
    sendRequest(context, request);
  }

  /**
   * Turns the LightMachine on.
   *
   * @param context    the {@link ActorContext} of the calling actor.
   * @param crossingId the railway crossing ID of the LightMachine.
   * @param trainSpeed the approaching train speed
   */
  public void lightOn(
    ActorContext<?> context,
    String crossingId,
    Double trainSpeed
  ) {
    byte[] body = buildRequestBody(trainSpeed);
    HttpRequest request = buildRequest("/light/on", crossingId, body);
    sendRequest(context, request);
  }

  /**
   * Turns the LightMachine off.
   *
   * @param context    the {@link ActorContext} of the calling actor.
   * @param crossingId the railway crossing ID of the LightMachine.
   */
  public void lightOff(ActorContext<?> context, String crossingId) {
    HttpRequest request = buildRequest("/light/off", crossingId);
    sendRequest(context, request);
  }

  /**
   * Turns on an early warning of the LightMachine
   *
   * @param context    the {@link ActorContext} of the calling actor.
   * @param crossingId the railway crossing ID of the LightMachine.
   */
  public void lightEarlyWarning(ActorContext<?> context, String crossingId) {
    HttpRequest request = buildRequest("/light/earlyWarning", crossingId);
    sendRequest(context, request);
  }
}
