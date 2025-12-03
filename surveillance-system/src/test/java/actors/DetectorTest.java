package actors;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import akka.actor.testkit.typed.javadsl.ActorTestKit;
import akka.actor.testkit.typed.javadsl.LoggingTestKit;
import akka.actor.testkit.typed.javadsl.TestProbe;
import akka.actor.typed.ActorRef;
import akka.actor.typed.javadsl.ActorContext;
import org.junit.Before;
import org.junit.Test;
import services.DetectorServices;

public class DetectorTest {

  private final DetectorServices detectorServices = mock(DetectorServices.class);

  static final ActorTestKit testKit = ActorTestKit.create();

  private static final String groupId = "group01";

  private static final String cameraId = "cam0";

  private final TestProbe<Surveillance.SurveillanceCommand> surveillance = testKit.createTestProbe(
    Surveillance.SurveillanceCommand.class
  );

  @Test
  public void detectorAlarmTest() {
    ActorRef<Detector.DetectorCommand> detector = testKit.spawn(
      Detector.create(groupId, cameraId, surveillance.getRef(), detectorServices)
    );

    LoggingTestKit.info("in state Processing").expect(testKit.system(), () -> {
      detector.tell(new Detector.CapturedImage(new byte[0]));
      return null;
    });
    verify(detectorServices, times(1)).cameraCapture(any(), any());
    LoggingTestKit.info("in state Alarm").expect(testKit.system(), () -> {
      detector.tell(new GlobalCommands.Alarm());
      return null;
    });
    LoggingTestKit.info("in state Capturing").expect(testKit.system(), () -> {
      detector.tell(new GlobalCommands.Disarm());
      return null;
    });
    verify(detectorServices, times(2)).cameraCapture(any(), any());
  }

  @Test
  public void detectorTimeoutTest() {
    ActorRef<Detector.DetectorCommand> detector = testKit.spawn(
      Detector.create(groupId, cameraId, surveillance.getRef(), detectorServices)
    );

    LoggingTestKit.info("in state Processing").expect(testKit.system(), () -> {
      detector.tell(new Detector.CapturedImage(new byte[0]));
      return null;
    });
    verify(detectorServices, times(1)).cameraCapture(any(), any());

    LoggingTestKit.info("in state Capturing").expect(testKit.system(), () -> {
      //Wait for Timeout (500ms)
      return null;
    });
  }
}
