package actors.guardian;

import actors.Command;
import actors.NodeConfig;
import actors.setup.BellSetup;
import actors.setup.ControllerSetup;
import actors.setup.GateSetup;
import actors.setup.LightMachineSetup;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import akka.discovery.Discovery;
import akka.discovery.ServiceDiscovery;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.time.Duration;

import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.semconv.ServiceAttributes;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.semconv.ResourceAttributes;
import open_telemetry.TelemetryJaeger;
import service.RailwayService;

public class Guardian extends AbstractBehavior<Command> {

  private enum ConfigStatus {
    Success,
    Failure
  }

  private RailwayService railwayService;

  private final String configPath;

  private NodeConfig config;

  private OpenTelemetry openTelemetry;

  public static Behavior<Command> create(String configPath) {
    return Behaviors.setup(context -> new Guardian(context, configPath));
  }

  public Guardian(ActorContext<Command> context, String configPath) {
    super(context);
    this.configPath = configPath;
    if (getNodeConfig() == ConfigStatus.Success) {
      ServiceDiscovery discovery = Discovery.get(context.getSystem()).discovery();
      railwayService = new RailwayService(discovery, config);
      railwayService.setupService(context);
      openTelemetry = TelemetryJaeger.initOpenTelemetry(config);
      setupComponent();
    }
  }

  @Override
  public Receive<Command> createReceive() {
    return newReceiveBuilder().build();
  }

  private ConfigStatus getNodeConfig() {
    try {
      ObjectMapper mapper = new ObjectMapper();
      config = mapper.readValue(new File(configPath), NodeConfig.class);
      getContext().getLog().info("Configuration loaded successfully:");
      config
        .crossings()
        .forEach(crossing -> {
          getContext().getLog().info("Crossing ID: {}", crossing.crossingId());
          getContext().getLog().info("Components: {}", crossing.components());
        });
      getContext().getLog().info("Service Location: {}", config.service_location());
      getContext().getLog().info("Service Name: {}", config.remote_service_name());
      getContext().getLog().info("Nats IP: {}", config.nats_server_addr());
      getContext().getLog().info("Nats Port: {}", config.nats_server_port());
      return ConfigStatus.Success;
    } catch (Exception e) {
      getContext().getLog().error("Error parsing ConfigFile: {}", e.getMessage());
      getContext().getSystem().terminate();
      return ConfigStatus.Failure;
    }
  }

  private void setupComponent() {
    config
      .crossings()
      .forEach(crossing ->
        crossing
          .components()
          .forEach(type -> {
            switch (type) {
              case Controller -> {
                getContext().spawn(
                  ControllerSetup.create(crossing.crossingId(), config),
                  "ControllerSetup" + crossing.crossingId()
                );
                getContext().getLog().info("ControllerSetup has been started successfully");
              }
              case LightMachine -> {
                getContext().spawn(
                  LightMachineSetup.create(crossing.crossingId(), railwayService),
                  "LightMachineSetup" + crossing.crossingId()
                );
                getContext().getLog().info("LightMachineSetup has been started successfully");
              }
              case Gate -> {
                getContext().spawn(
                  GateSetup.create(crossing.crossingId(), railwayService),
                  "GateSetup" + crossing.crossingId()
                );
                getContext().getLog().info("GateSetup has been started successfully");
              }
              case Bell -> {
                getContext().spawn(
                  BellSetup.create(crossing.crossingId(), railwayService),
                  "BellSetup" + crossing.crossingId()
                );
                getContext().getLog().info("BellSetup has been started successfully");
              }
              default -> getContext().getLog().info("No Rule defined for Component_Type {}", type);
            }
          })
      );
  }
}
