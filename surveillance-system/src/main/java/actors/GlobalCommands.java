package actors;

public class GlobalCommands {

  public static class Alarm implements Detector.DetectorCommand, Surveillance.SurveillanceCommand {}

  public static class Disarm
    implements Detector.DetectorCommand, Surveillance.SurveillanceCommand {}
}
