package actors.common;

import actors.Detector;
import actors.Surveillance;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Commands shared across detectors and surveillance actors. */
public class SharedCommands {

  /** Triggers an Alarm */
  public static class Alarm
    implements Detector.DetectorCommand, Surveillance.SurveillanceCommand {}

  /** Disarms the system. */
  public static class Disarm
    implements Detector.DetectorCommand, Surveillance.SurveillanceCommand {}

  /**
   * Indicates a failure during a service invocation.
   *
   * @param serviceName   Name of the service that failed.
   */
  public record InvocationFailure(String serviceName) implements
    Detector.DetectorCommand, Surveillance.SurveillanceCommand {
    @JsonCreator
    public InvocationFailure(@JsonProperty("serviceName") String serviceName) {
      this.serviceName = serviceName;
    }
  }
}
