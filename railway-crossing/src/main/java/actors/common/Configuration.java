package actors.common;

import akka.actor.typed.javadsl.ActorContext;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.util.List;

/**
 * Loads and holds the Configuration for the Railway-Components
 */
public class Configuration {

  private static NodeConfiguration nodeConfiguration = null;

  /**
   * Initializes the configuration from a JSON file.
   *
   * @return Success if loaded correctly, Failure otherwise.
   */
  public static ConfigStatus initConfig(ActorContext<?> context, String configPath){
    if(nodeConfiguration == null){
      try {
        ObjectMapper mapper = new ObjectMapper();
        nodeConfiguration = mapper.readValue(new File(configPath), NodeConfiguration.class);
        context.getLog().info("Configuration loaded successfully:");
        nodeConfiguration
                .crossings()
                .forEach(crossing -> {
                  context.getLog().info("Crossing ID: {}", crossing.crossingId());
                  context.getLog().info("Components: {}", crossing.components());
                });
        context.getLog().info("Service at: {}:{}", nodeConfiguration.service_server_addr(), nodeConfiguration.service_server_port());
        context.getLog().info("Nats at: {}:{}", nodeConfiguration.nats_server_addr(),nodeConfiguration.nats_server_port());
      } catch (Exception e) {
        context.getLog().error("Error parsing ConfigFile: {}", e.getMessage());
        context.getSystem().terminate();
        nodeConfiguration = null;
        return ConfigStatus.Failure;
      }
    }
    return ConfigStatus.Success;
  }

  /** Returns the current NodeConfiguration */
  public static NodeConfiguration getNodeConfiguration() {
    return nodeConfiguration;
  }

  /** Results of loading the NodeConfig */
  public enum ConfigStatus {
    Success,
    Failure
  }

  /** Railway-Crossing Components */
  public enum ComponentType {
    Controller,
    LightMachine,
    Gate,
    Bell,
    None
  }

  /** Specifies which components to create for a specific Railway-Crossing */
  public record CrossingConfiguration(String crossingId, List<ComponentType> components) {}

  /** Specifies the Railway-Crossings, Location of Railway-Service, Location of NATS, Location of Telegraf */
  public record NodeConfiguration(
          List<CrossingConfiguration> crossings,
          String service_server_addr,
          int service_server_port,
          String nats_server_addr,
          int nats_server_port,
          String export_server_addr,
          int export_server_port
  ) {}
}
