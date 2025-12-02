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

  private static final String cameraId = "c0";

  private final TestProbe<Surveillance.SurveillanceCommand> surveillance = testKit.createTestProbe(
    Surveillance.SurveillanceCommand.class
  );

  @Before
  public void setup() {
    doAnswer(invocationOnMock -> {
      ActorContext<Detector.DetectorCommand> context = invocationOnMock.getArgument(0);
      context.getLog().info("alarmOn");
      return null;
    })
      .when(detectorServices)
      .alarmOn(any());

    doAnswer(invocationOnMock -> {
      ActorContext<Detector.DetectorCommand> context = invocationOnMock.getArgument(0);
      context.getLog().info("alarmOff");
      return null;
    })
      .when(detectorServices)
      .alarmOff(any());

    doAnswer(invocationOnMock -> {
      ActorContext<Detector.DetectorCommand> context = invocationOnMock.getArgument(0);
      context.getLog().info("captureCamera");
      return null;
    })
      .when(detectorServices)
      .cameraCapture(any(), any());

    doAnswer(invocationOnMock -> {
      ActorContext<Detector.DetectorCommand> context = invocationOnMock.getArgument(0);
      context.getLog().info("detectPersons");
      return null;
    })
      .when(detectorServices)
      .detectPersons(any(), any());
  }

  @Test
  public void detectorAlarmTest() {
    ActorRef<Detector.DetectorCommand> detector = testKit.spawn(
      Detector.create(cameraId, surveillance.getRef(), detectorServices)
    );

    LoggingTestKit.info("detectPersons").expect(testKit.system(), () -> {
      detector.tell(new Detector.CapturedImage(new byte[0]));
      return null;
    });
    verify(detectorServices, times(1)).cameraCapture(any(), any());
    LoggingTestKit.info("alarmOn").expect(testKit.system(), () -> {
      detector.tell(new GlobalCommands.Alarm());
      return null;
    });
    LoggingTestKit.info("alarmOff").expect(testKit.system(), () -> {
      detector.tell(new GlobalCommands.Disarm());
      return null;
    });
    verify(detectorServices, times(2)).cameraCapture(any(), any());
  }

  @Test
  public void detectorTimeoutTest() {
    ActorRef<Detector.DetectorCommand> detector = testKit.spawn(
      Detector.create(cameraId, surveillance.getRef(), detectorServices)
    );

    LoggingTestKit.info("detectPersons").expect(testKit.system(), () -> {
      detector.tell(new Detector.CapturedImage(new byte[0]));
      return null;
    });
    verify(detectorServices, times(1)).cameraCapture(any(), any());

    LoggingTestKit.info("captureCamera").expect(testKit.system(), () -> {
      //Wait for Timeout
      return null;
    });
  }
}
