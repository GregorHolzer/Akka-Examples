package actors.common;

import akka.actor.typed.javadsl.ActorContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.util.List;

/**
 * Loads and holds the Configuration for the Surveillance-Components
 */
public class Configuration {

  private static NodeConfiguration nodeConfiguration = null;

  /**
   * Initializes the configuration from the specified JSON file.
   *
   * @param context {@link ActorContext} used for logging.
   * @param configPath the path to the JSON configuration file.
   * @return {@link ConfigurationStatus#Success} if the configuration is loaded successfully, {@link ConfigurationStatus#Failure} otherwise.
   */
  public static ConfigurationStatus initConfig(ActorContext<?> context, String configPath) {
    if (nodeConfiguration == null) {
      try {
        ObjectMapper mapper = new ObjectMapper();
        nodeConfiguration = mapper.readValue(new File(configPath), NodeConfiguration.class);
        context.getLog().info("actors.common.Configuration loaded successfully:");
        nodeConfiguration.detectorsConfigs.forEach(detector ->
          context
            .getLog()
            .info(
              "Launching Detector with cameraId: {} responding to SurveillanceId {}",
              detector.cameraId,
              detector.surveillanceId
            )
        );
        nodeConfiguration.surveillanceConfigs.forEach(surveillance ->
          context
            .getLog()
            .info("Launched Surveillance with SurveillanceId: {}", surveillance.surveillanceId)
        );
        context
          .getLog()
          .info("Configured Cloud-Service Address: {}", nodeConfiguration.cloud_service_addr);
        context
          .getLog()
          .info("Configured Cloud-Service Port: {}", nodeConfiguration.cloud_service_port);
        context
          .getLog()
          .info("Configured Edge-Service Address: {}", nodeConfiguration.edge_service_addr);
        context
          .getLog()
          .info("Configured Edge-Service Port: {}", nodeConfiguration.edge_service_port);
        context
          .getLog()
          .info("Configured IoT-Service Address: {}", nodeConfiguration.iot_service_addr);
        context
          .getLog()
          .info("Configured IoT-Service Port: {}", nodeConfiguration.iot_service_port);
      } catch (Exception e) {
        context.getLog().error("Error parsing ConfigFile: {}", e.getMessage());
        context.getSystem().terminate();
        return ConfigurationStatus.Failure;
      }
    }
    return ConfigurationStatus.Success;
  }

  /** Returns the current NodeConfiguration */
  public static NodeConfiguration getNodeConfiguration() {
    return nodeConfiguration;
  }

  /** Results of loading the NodeConfig */
  public enum ConfigurationStatus {
    Success,
    Failure
  }

  /** Detector Configuration */
  public record DetectorConfiguration(String detectorId, Integer cameraId, String surveillanceId) {}

  /** Surveillance Configuration */
  public record SurveillanceConfiguration(String surveillanceId) {}

  /** Node Configuration holding multiple {@link DetectorConfiguration}s and {@link SurveillanceConfiguration}s. */
  public record NodeConfiguration(
    List<DetectorConfiguration> detectorsConfigs,
    List<SurveillanceConfiguration> surveillanceConfigs,
    String cloud_service_addr,
    Integer cloud_service_port,
    String edge_service_addr,
    Integer edge_service_port,
    String iot_service_addr,
    Integer iot_service_port
  ) {}
}
