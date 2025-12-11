package actors.common;

import actors.Detector;
import actors.Surveillance;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class GlobalCommands {

  public static class Alarm implements Detector.DetectorCommand, Surveillance.SurveillanceCommand {}

  public static class Disarm
    implements Detector.DetectorCommand, Surveillance.SurveillanceCommand {}

  public record InvocationFailure(String serviceName) implements Detector.DetectorCommand,  Surveillance.SurveillanceCommand {

    @JsonCreator
    public InvocationFailure(@JsonProperty("serviceName") String serviceName) {
      this.serviceName = serviceName;
    }
  }
}
