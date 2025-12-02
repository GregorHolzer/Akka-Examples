package actors.global_commands;

import actors.Command;
import actors.Detector.Detector;
import actors.Surveillance.Surveillance;

public class GlobalCommands {

    public static class Alarm implements Detector.DetectorCommand, Surveillance.SurveillanceCommand {}

    public static class Disarm implements Detector.DetectorCommand, Surveillance.SurveillanceCommand {}
}
