package actors;

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
        context.getLog().info("actors.Configuration loaded successfully:");
        nodeConfiguration.detectors.forEach(detector -> {
          context
            .getLog()
            .info(
              "Launching Detector with cameraId: {} responding to Surveillance with id {}",
              detector.cameraId,
              detector.surveillanceId
            );
        });
        nodeConfiguration.surveillanceIds.forEach(surveillanceId -> {
          context.getLog().info("Launched Surveillance with Id: {}", surveillanceId);
        });
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

  public record DetectorConfiguration(String cameraId, String surveillanceId) {}

  public record NodeConfiguration(
    List<DetectorConfiguration> detectors,
    List<String> surveillanceIds
  ) {}
}
