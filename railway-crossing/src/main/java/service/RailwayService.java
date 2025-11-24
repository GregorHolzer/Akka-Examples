package service;

import actors.Command;
import actors.NodeConfig;
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
import java.util.Optional;

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

  private void sendRequest(
    ActorContext<?> context,
    Optional<ActorRef<InvocationResponse>> reply,
    String path,
    String crossingId,
    Optional<Double> trainSpeed
  ) {
    String url = "";
    switch (nodeConfig.service_location()) {
      case Remote -> url = "http:/" + address + ":" + port + path;
      case Local -> url = "http://localhost" + ":" + port + path;
    }
    HttpRequest request;
    if (trainSpeed.isPresent()) {
      ContextVariables.Builder var = ContextVariables.newBuilder();
      var.addData(
        ContextVariable.newBuilder()
          .setName("approachingSpeed")
          .setValue(Value.newBuilder().setDouble(trainSpeed.get()).build())
          .build()
      );
      byte[] body = var.build().toByteArray();
      request = HttpRequest.POST(url)
        .addHeader(HttpHeader.parse("Cirrina-Sender-ID", crossingId))
        .withEntity(
          HttpEntities.create(ContentTypes.APPLICATION_OCTET_STREAM, ByteString.fromArray(body))
        );
    } else {
      request = HttpRequest.POST(url).addHeader(HttpHeader.parse("Cirrina-Sender-ID", crossingId));
    }

    Http.get(context.getSystem())
      .singleRequest(request)
      .whenComplete((httpResponse, throwable) -> {
        if (reply.isPresent()) {
          if (throwable != null) {
            reply.get().tell(new InvocationResponse(InvocationResult.Failure));
          } else if (httpResponse.status().equals(StatusCodes.OK)) {
            reply.get().tell(new InvocationResponse(InvocationResult.Success));
          } else {
            reply.get().tell(new InvocationResponse(InvocationResult.Failure));
          }
        }
      });
  }

  public void bellOn(ActorContext<?> context, String crossingId, Double trainSpeed) {
    sendRequest(context, Optional.empty(), "/bell/on", crossingId, Optional.of(trainSpeed));
  }

  public void bellOff(ActorContext<?> context, String crossingId) {
    sendRequest(context, Optional.empty(), "/bell/off", crossingId, Optional.empty());
  }

  public void gateUp(
    ActorContext<?> context,
    ActorRef<InvocationResponse> reply,
    String crossingId
  ) {
    sendRequest(context, Optional.of(reply), "/gate/up", crossingId, Optional.empty());
  }

  public void gateDown(ActorContext<?> context, String crossingId, Double trainSpeed) {
    sendRequest(context, Optional.empty(), "/gate/down", crossingId, Optional.of(trainSpeed));
  }

  public void lightOn(ActorContext<?> context, String crossingId, Double trainSpeed) {
    sendRequest(context, Optional.empty(), "/light/on", crossingId, Optional.of(trainSpeed));
  }

  public void lightOff(ActorContext<?> context, String crossingId) {
    sendRequest(context, Optional.empty(), "/light/off", crossingId, Optional.empty());
  }

  public void lightEarlyWarning(ActorContext<?> context, String crossingId) {
    sendRequest(context, Optional.empty(), "/light/earlyWarning", crossingId, Optional.empty());
  }
}
