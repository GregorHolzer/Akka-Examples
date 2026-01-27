package actors.services;

import actors.Bell;
import actors.Gate;
import actors.LightMachine;
import actors.common.Configuration;
import akka.actor.typed.ActorRef;
import akka.actor.typed.javadsl.ActorContext;
import akka.http.javadsl.model.*;
import java.util.HashMap;
import java.util.concurrent.CompletionStage;

/**
 * Provides functionality to invoke the Railway Service via HTTP.
 *
 * <p>This class builds and sends HTTP requests to the configured
 * Railway Service endpoints using Akka HTTP.</p>
 */
public class RailwayService implements AkkaService{

  private final Configuration.NodeConfiguration nodeConfig;

  /**
   * Creates a new {@code RailwayService} using the node configuration.
   */
  public RailwayService() {
    this.nodeConfig = Configuration.getNodeConfiguration();
  }


  /**
   * Turns the bell on.
   *
   * @param context    the {@link ActorContext} of the calling actor.
   * @param crossingId the railway crossing ID of the bell.
   * @param cmd        the {@link actors.Bell.CommandBellOn} command.
   */
  public void bellOn(
    ActorContext<?> context,
    String crossingId,
    Bell.CommandBellOn cmd
  ) {
    HashMap<String, Object> vars = new HashMap<>();
    vars.put("approachingSpeed", cmd.trainSpeed());
    vars.put("traceId", cmd.traceId());
    vars.put("spanId", cmd.spanId());
    byte[] body = buildProtoRequestBody(vars);
    HttpRequest request = buildPostRequest(
            nodeConfig.service_server_addr(),
            nodeConfig.service_server_port(),
            "/bell/on",
            HttpHeader.parse("Cirrina-Sender-ID", crossingId),
            body);
    sendRequest(context, request);
  }

  /**
   * Turns the bell off.
   *
   * @param context    the {@link ActorContext} of the calling actor.
   * @param crossingId the railway crossing ID of the bell.
   * @param cmd        the {@link actors.Bell.CommandBellOff} command.

   */
  public void bellOff(
    ActorContext<?> context,
    String crossingId,
    Bell.CommandBellOff cmd
  ) {
    HashMap<String, Object> vars = new HashMap<>();
    vars.put("traceId", cmd.traceId());
    vars.put("spanId", cmd.spanId());
    byte[] body = buildProtoRequestBody(vars);
    HttpRequest request = buildPostRequest(
            nodeConfig.service_server_addr(),
            nodeConfig.service_server_port(),
            "/bell/off",
            HttpHeader.parse("Cirrina-Sender-ID", crossingId),
            body);
    sendRequest(context, request);
  }

  /**
   * Opens the gate and sends a {@link Bell.CommandBellOff} message to the Bell actor after successful invocation.
   *
   * @param context    the {@link ActorContext} of the calling actor.
   * @param bell       reference to the bell actor.
   * @param crossingId the railway crossing ID of the gate.
   * @param cmd        the {@link actors.Gate.CommandOpen} command
   */
  public void gateUp(
    ActorContext<?> context,
    ActorRef<Bell.BellCommand> bell,
    String crossingId,
    Gate.CommandOpen cmd
  ) {
    HashMap<String, Object> vars = new HashMap<>();
    vars.put("traceId", cmd.traceId);
    vars.put("spanId", cmd.spanId);
    byte[] body = buildProtoRequestBody(vars);
    HttpRequest request = buildPostRequest(
            nodeConfig.service_server_addr(),
            nodeConfig.service_server_port(),
            "/gate/up",
            HttpHeader.parse("Cirrina-Sender-ID", crossingId),
            body);
    sendRequest(context, request);
  }

  /**
   * Closes the gate.
   *
   * @param context    the {@link ActorContext} of the calling actor.
   * @param crossingId the railway crossing ID of the gate.
   * @param cmd        the {@link Gate.CommandClose} command
   */
  public void gateDown(
    ActorContext<?> context,
    String crossingId,
    Gate.CommandClose cmd
  ) {
    HashMap<String, Object> vars = new HashMap<>();
    vars.put("approachingSpeed", cmd.trainSpeed);
    vars.put("traceId", cmd.traceId);
    vars.put("spanId", cmd.spanId);
    byte[] body = buildProtoRequestBody(vars);
    HttpRequest request = buildPostRequest(
            nodeConfig.service_server_addr(),
            nodeConfig.service_server_port(),
            "/gate/down",
            HttpHeader.parse("Cirrina-Sender-ID", crossingId),
            body);
    CompletionStage<HttpResponse> future = sendRequest(context, request);
    future.whenComplete((response, throwable) -> {
      if (throwable != null ) {
        System.out.println("Railway service error: /gate/down: " + throwable.getMessage());
      }
      if(response != null && response.status() != StatusCodes.OK) {
        System.out.println("Status Code: /gate/down: " + response.status());
      }
    });
  }

  /**
   * Turns the LightMachine on.
   *
   * @param context    the {@link ActorContext} of the calling actor.
   * @param crossingId the railway crossing ID of the LightMachine.
   * @param cmd        the {@link LightMachine.CommandTurnOn} command
   */
  public void lightOn(
    ActorContext<?> context,
    String crossingId,
    LightMachine.CommandTurnOn cmd
  ) {
    HashMap<String, Object> vars = new HashMap<>();
    vars.put("approachingSpeed", cmd.trainSpeed());
    vars.put("traceId", cmd.traceId());
    vars.put("spanId", cmd.spanId());
    byte[] body = buildProtoRequestBody(vars);
    HttpRequest request = buildPostRequest(
            nodeConfig.service_server_addr(),
            nodeConfig.service_server_port(),
            "/light/on",
            HttpHeader.parse("Cirrina-Sender-ID", crossingId),
            body);
    sendRequest(context, request);
  }

  /**
   * Turns the LightMachine off.
   *
   * @param context    the {@link ActorContext} of the calling actor.
   * @param crossingId the railway crossing ID of the LightMachine.
   */
  public void lightOff(ActorContext<?> context, String crossingId, LightMachine.CommandTurnOff cmd) {
    HashMap<String, Object> vars = new HashMap<>();
    vars.put("traceId", cmd.traceId());
    vars.put("spanId", cmd.spanId());
    byte[] body = buildProtoRequestBody(vars);
    HttpRequest request = buildPostRequest(
            nodeConfig.service_server_addr(),
            nodeConfig.service_server_port(),
            "/light/off",
            HttpHeader.parse("Cirrina-Sender-ID", crossingId),
            body);
    sendRequest(context, request);
  }

  /**
   * Turns on an early warning of the LightMachine
   *
   * @param context    the {@link ActorContext} of the calling actor.
   * @param crossingId the railway crossing ID of the LightMachine.
   */
  public void lightEarlyWarning(ActorContext<?> context, String crossingId, LightMachine.CommandEarlyMorning cmd) {
    HashMap<String, Object> vars = new HashMap<>();
    vars.put("traceId", cmd.traceId());
    vars.put("spanId", cmd.spanId());
    byte[] body = buildProtoRequestBody(vars);
    HttpRequest request = buildPostRequest(
            nodeConfig.service_server_addr(),
            nodeConfig.service_server_port(),
            "/light/earlyWarning",
            HttpHeader.parse("Cirrina-Sender-ID", crossingId),
            body);
    sendRequest(context, request);
  }
}
