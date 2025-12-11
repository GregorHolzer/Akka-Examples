package service;

import actors.common.Command;
import actors.common.NodeConfig;
import actors.Bell;
import akka.actor.typed.ActorRef;
import akka.actor.typed.javadsl.ActorContext;
import akka.discovery.ServiceDiscovery;
import akka.http.javadsl.Http;
import akka.http.javadsl.model.*;
import akka.util.ByteString;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import exchange.ContextVariableProtos.*;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.concurrent.CompletionStage;

public class RailwayService {

  public enum InvocationResult {
    Success,
    Failure
  }

  public static class InvocationResponse implements Command {

    public InvocationResult result;

    @JsonCreator
    public InvocationResponse(@JsonProperty("result") InvocationResult result) {
      this.result = result;
    }
  }

  public static final Integer port = 8000;

  private static final Duration timeout = Duration.ofSeconds(10);

  private final NodeConfig nodeConfig;

  private final ServiceDiscovery discovery;

  private InetAddress address;

  public RailwayService(ServiceDiscovery discovery, NodeConfig nodeConfig) {
    this.discovery = discovery;
    this.nodeConfig = nodeConfig;
  }

  public void setupService(ActorContext<?> context) {
    switch (nodeConfig.service_location()) {
      case Remote -> this.discover(context);
      case Local -> {
        try {
          address = InetAddress.getByName("localhost");
          context.getLog().info("Using Local Service: address: {}", address);
        } catch (UnknownHostException e) {
          context.getLog().error("Unable to get Localhost: {}", e.getMessage());
          context.getSystem().terminate();
        }
      }
    }
  }

  public void discover(ActorContext<?> context) {
    while (true) {
      try {
        ServiceDiscovery.Resolved resolved = discovery
          .lookup(nodeConfig.remote_service_name(), timeout)
          .toCompletableFuture()
          .get();
        if (resolved.getAddresses().isEmpty()) {
          context.getLog().error("Discovery contains no service, retrying...");
        } else if (resolved.getAddresses().getFirst().getAddress().isEmpty()) {
          context.getLog().error("Resolved Service contains no address, retrying...");
        } else {
          address = resolved.getAddresses().getFirst().getAddress().get();
          context.getLog().info("Resolved Service contains address: {}", address);
          break;
        }
      } catch (Exception e) {
        context
          .getLog()
          .error(
            "Failed to discover service {} after {}, message: {}, retrying...",
            nodeConfig.remote_service_name(),
            timeout,
            e.getMessage()
          );
      }
    }
  }

  private String getUrl(String path) {
    String url = "";
    switch (nodeConfig.service_location()) {
      case Remote -> url = "http:/" + address + ":" + port + path;
      case Local -> url = "http://localhost" + ":" + port + path;
    }
    return url;
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
