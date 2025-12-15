package actors.common;

import actors.Bell;
import akka.actor.typed.ActorRef;
import akka.actor.typed.javadsl.ActorContext;
import akka.http.javadsl.Http;
import akka.http.javadsl.model.*;
import akka.util.ByteString;
import exchange.ContextVariableProtos.*;
import java.util.concurrent.CompletionStage;

public class RailwayService {

  private final Configuration.NodeConfiguration nodeConfig;

  public RailwayService() {
    this.nodeConfig = Configuration.getNodeConfiguration();
  }

  private String getUrl(String path) {
    return  "http://" + nodeConfig.service_server_addr() + ":" + nodeConfig.service_server_port() + path;
  }

  private CompletionStage<HttpResponse> sendRequest(ActorContext<?> context, HttpRequest request) {
    return Http.get(context.getSystem()).singleRequest(request);
  }

  private HttpRequest buildRequest(String path, String crossingId) {
    String url = getUrl(path);
    return HttpRequest.POST(url).addHeader(HttpHeader.parse("Cirrina-Sender-ID", crossingId));
  }

  private HttpRequest buildRequest(String path, String crossingId, byte[] body) {
    String url = getUrl(path);
    return HttpRequest.POST(url)
      .addHeader(HttpHeader.parse("Cirrina-Sender-ID", crossingId))
      .withEntity(
        HttpEntities.create(ContentTypes.APPLICATION_OCTET_STREAM, ByteString.fromArray(body))
      );
  }

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

  public void bellOn(ActorContext<?> context, String crossingId, Double trainSpeed) {
    byte[] body = buildRequestBody(trainSpeed);
    HttpRequest request = buildRequest("/bell/on", crossingId, body);
    sendRequest(context, request);
  }

  public void bellOff(ActorContext<?> context, String crossingId, String traceId, String spanId) {
    byte[] body = buildRequestBody(traceId, spanId);
    HttpRequest request = buildRequest("/bell/off", crossingId, body);
    sendRequest(context, request);
  }

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

  public void gateDown(ActorContext<?> context, String crossingId, Double trainSpeed) {
    byte[] body = buildRequestBody(trainSpeed);
    HttpRequest request = buildRequest("/gate/down", crossingId, body);
    sendRequest(context, request);
  }

  public void lightOn(ActorContext<?> context, String crossingId, Double trainSpeed) {
    byte[] body = buildRequestBody(trainSpeed);
    HttpRequest request = buildRequest("/light/on", crossingId, body);
    sendRequest(context, request);
  }

  public void lightOff(ActorContext<?> context, String crossingId) {
    HttpRequest request = buildRequest("/light/off", crossingId);
    sendRequest(context, request);
  }

  public void lightEarlyWarning(ActorContext<?> context, String crossingId) {
    HttpRequest request = buildRequest("/light/earlyWarning", crossingId);
    sendRequest(context, request);
  }
}
