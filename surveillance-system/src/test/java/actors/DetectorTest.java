package actors;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import actors.common.SharedCommands;
import akka.actor.testkit.typed.javadsl.ActorTestKit;
import akka.actor.testkit.typed.javadsl.LoggingTestKit;
import akka.actor.testkit.typed.javadsl.TestProbe;
import akka.actor.typed.ActorRef;
import org.junit.Test;
import services.DetectorService;

public class DetectorTest {

  private final DetectorService detectorService = mock(DetectorService.class);

  static final ActorTestKit testKit = ActorTestKit.create();

  private static final Integer cameraId = 1;

  private final TestProbe<Surveillance.SurveillanceCommand> surveillance =
    testKit.createTestProbe(Surveillance.SurveillanceCommand.class);

  @Test
  public void detectorAlarmTest() {
    ActorRef<Detector.DetectorCommand> detector = testKit.spawn(
      Detector.create(cameraId, surveillance.getRef(), detectorService, 1000),
      "detector_alarm"
    );

    LoggingTestKit.info("in state Processing").expect(testKit.system(), () -> {
      detector.tell(new Detector.CapturedImage(new byte[0]));
      return null;
    });
    verify(detectorService, times(1)).cameraCapture(any(), any());
    LoggingTestKit.info("in state Alarm").expect(testKit.system(), () -> {
      detector.tell(new SharedCommands.Alarm());
      return null;
    });
    LoggingTestKit.info("in state Capturing").expect(testKit.system(), () -> {
      detector.tell(new SharedCommands.Disarm());
      return null;
    });
    verify(detectorService, times(2)).cameraCapture(any(), any());
  }

  @Test
  public void detectorTimeoutTest() {
    ActorRef<Detector.DetectorCommand> detector = testKit.spawn(
      Detector.create(cameraId, surveillance.getRef(), detectorService, 1500),
      "detector_timeout"
    );

    LoggingTestKit.info("in state Processing").expect(testKit.system(), () -> {
      detector.tell(new Detector.CapturedImage(new byte[0]));
      return null;
    });
    verify(detectorService, times(1)).cameraCapture(any(), any());

    LoggingTestKit.info("in state Capturing").expect(testKit.system(), () -> {
      //Wait for Timeout (500ms)
      return null;
    });
  }
}
