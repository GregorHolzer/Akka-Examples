package actors.common;

import akka.actor.typed.javadsl.ActorContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.util.List;

public class Configuration {

  private static NodeConfiguration nodeConfiguration = null;

  public static ConfigurationStatus initConfig(ActorContext<?> context, String configPath) {
    if (nodeConfiguration == null) {
      try {
        ObjectMapper mapper = new ObjectMapper();
        nodeConfiguration = mapper.readValue(new File(configPath), NodeConfiguration.class);
        context.getLog().info("actors.common.Configuration loaded successfully:");
        nodeConfiguration.detectorsConfigs.forEach(detector ->
                context.getLog().info("Launching Detector within Group: {} with cameraId: {} responding to SurveillanceId {}",
            detector.groupId,
            detector.cameraId,
            detector.surveillanceId
          ));
        nodeConfiguration.surveillanceConfigs.forEach(surveillance ->
                context.getLog().info("Launched Surveillance within Group: {} with SurveillanceId: {}",
                surveillance.groupId,
                surveillance.surveillanceId
        ));
      } catch (Exception e) {
        context.getLog().error("Error parsing ConfigFile: {}", e.getMessage());
        context.getSystem().terminate();
        return ConfigurationStatus.Failure;
      }
    }
    return ConfigurationStatus.Success;
  }

  public static NodeConfiguration getNodeConfiguration() {
    return nodeConfiguration;
  }

  public enum ConfigurationStatus {
    Success,
    Failure
  }

  public record DetectorConfiguration(
          String groupId,
          String detectorId,
          Integer cameraId,
          String surveillanceId) {}

  public record SurveillanceConfiguration(
          String groupId,
          String surveillanceId
  ){}

  public record NodeConfiguration(
    List<DetectorConfiguration> detectorsConfigs,
    List<SurveillanceConfiguration> surveillanceConfigs
  ) {}
}
