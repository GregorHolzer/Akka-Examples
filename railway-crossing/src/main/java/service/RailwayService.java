package service;

import actors.NodeConfig;
import akka.actor.typed.javadsl.ActorContext;
import akka.discovery.ServiceDiscovery;
import exchange.ContextVariableProtos.*;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

public class RailwayService {

  public static final Integer port = 8080;

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

  private void sendRequest(ActorContext<?> context, String path, String crossingId, Optional<Double> trainSpeed) {
    String url = "";
    switch (nodeConfig.service_location()) {
      case Remote -> url = "http:/" + address + ":" + port + path;
      case Local -> url = "http://localhost" + ":" + port + path;
    }
    try {
      HttpClient client = HttpClient.newHttpClient();
        HttpRequest request;
        if(trainSpeed.isPresent()) {
            ContextVariables.Builder var = ContextVariables.newBuilder();
            var.addData(
                    ContextVariable.newBuilder()
                            .setName("approachingSpeed")
                            .setValue(
                                    Value.newBuilder()
                                            .setDouble(trainSpeed.get())
                                            .build()
                            )
                            .build()
            );
            byte[] body = var.build().toByteArray();
            request = HttpRequest.newBuilder()
                  .uri(URI.create(url))
                  .header("Cirrina-Sender-ID", crossingId)
                  .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                  .build();
      }
      else{
          request = HttpRequest.newBuilder()
                  .uri(URI.create(url))
                  .header("Cirrina-Sender-ID", crossingId)
                  .POST(HttpRequest.BodyPublishers.noBody())
                  .build();
      }

      HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
      context
        .getLog()
        .info(
          "Sent request to {}, Response code: {}, body: {}",
          path,
          response.statusCode(),
          response.body()
        );
    } catch (Exception e) {
      context.getLog().error("Failed to call {}: {}", path, e.getMessage());
    }
  }

  public void bellOn(ActorContext<?> context, String crossingId) {
    sendRequest(context, "/bell/on", crossingId, Optional.empty());
  }

  public void bellOff(ActorContext<?> context, String crossingId) {
    sendRequest(context, "/bell/off", crossingId, Optional.empty());
  }

  public void gateUp(ActorContext<?> context, String crossingId) {
    sendRequest(context, "/gate/up", crossingId, Optional.empty());
  }

  public void gateDown(ActorContext<?> context, String crossingId) {
    sendRequest(context, "/gate/down", crossingId, Optional.empty());
  }

  public void lightOn(ActorContext<?> context, String crossingId) {
    sendRequest(context, "/light/on", crossingId, Optional.empty());
  }

  public void lightOff(ActorContext<?> context, String crossingId) {
    sendRequest(context, "/light/off", crossingId, Optional.empty());
  }
}
