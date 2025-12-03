package actors;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import akka.actor.testkit.typed.javadsl.ActorTestKit;
import akka.actor.testkit.typed.javadsl.LoggingTestKit;
import akka.actor.testkit.typed.javadsl.TestProbe;
import akka.actor.typed.ActorRef;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.receptionist.Receptionist;
import java.time.Duration;

import akka.actor.typed.receptionist.ServiceKey;
import org.junit.Before;
import org.junit.Test;
import services.SurveillanceServices;

public class SurveillanceTest {

  private static final String groupId = "group";

  private static final ServiceKey<Detector.DetectorCommand> groupDetectorKey1 = ServiceKey.create(Detector.DetectorCommand.class, groupId + "01");

  private static final ServiceKey<Detector.DetectorCommand> groupDetectorKey2 = ServiceKey.create(Detector.DetectorCommand.class, groupId + "02");

  private static final ServiceKey<Detector.DetectorCommand> groupDetectorKey3 = ServiceKey.create(Detector.DetectorCommand.class, groupId + "03");

  private final SurveillanceServices surveillanceServices = mock(SurveillanceServices.class);

  static final ActorTestKit testKit = ActorTestKit.create();

  private final TestProbe<Detector.DetectorCommand> detector = testKit.createTestProbe("detector",
    Detector.DetectorCommand.class
  );

  @Before
  public void setup() {
    doAnswer(invocationOnMock -> {
      ActorContext<Detector.DetectorCommand> context = invocationOnMock.getArgument(0);
      context.getLog().info("analyze");
      return null;
    })
      .when(surveillanceServices)
      .analyze(any(), any());
    testKit
      .system()
      .receptionist()
      .tell(Receptionist.register(groupDetectorKey1, detector.getRef()));
    testKit
            .system()
            .receptionist()
            .tell(Receptionist.register(groupDetectorKey2, detector.getRef()));
    testKit
            .system()
            .receptionist()
            .tell(Receptionist.register(groupDetectorKey3, detector.getRef()));
  }

  @Test
  public void surveillanceTestNoAlarm() {
    ActorRef<Surveillance.SurveillanceCommand> surveillance = testKit.spawn(
      Surveillance.create(surveillanceServices, groupId + "01", "test1"), "test1"
    );

    LoggingTestKit.info("analyze").expect(testKit.system(), () -> {
      surveillance.tell(new Surveillance.FoundPersons(new byte[0]));
      return null;
    });

    LoggingTestKit.info("")
      .withOccurrences(0)
      .expect(testKit.system(), () -> {
        surveillance.tell(new Surveillance.Analyzed(new byte[0], false));
        return null;
      });
  }

  @Test
  public void surveillanceTestAlarmManualDisarm() {
    ActorRef<Surveillance.SurveillanceCommand> surveillance = testKit.spawn(
      Surveillance.create(surveillanceServices, groupId + "02", "test2"), "test2"
    );

    LoggingTestKit.info("analyze").expect(testKit.system(), () -> {
      surveillance.tell(new Surveillance.FoundPersons(new byte[0]));
      return null;
    });

    LoggingTestKit.info("in state Alarm")
      .expect(testKit.system(), () -> {
        surveillance.tell(new Surveillance.Analyzed(new byte[0], true));
        return null;
      });
    detector.expectMessageClass(GlobalCommands.Alarm.class);

    LoggingTestKit.info("in state Processing")
      .expect(testKit.system(), () -> {
        surveillance.tell(new GlobalCommands.Disarm());
        return null;
      });
    LoggingTestKit.info("analyze").expect(testKit.system(), () -> {
      surveillance.tell(new Surveillance.FoundPersons(new byte[0]));
      return null;
    });
  }

  @Test
  public void surveillanceTestAlarmTimeoutDisarm() {
    ActorRef<Surveillance.SurveillanceCommand> surveillance = testKit.spawn(
      Surveillance.create(surveillanceServices, groupId + "03",  "test3"), "test3"
    );

    LoggingTestKit.info("in state Alarm")
      .expect(testKit.system(), () -> {
        surveillance.tell(new GlobalCommands.Alarm());
        return null;
      });
    LoggingTestKit.info("analyze")
      .withOccurrences(0)
      .expect(testKit.system(), () -> {
        surveillance.tell(new Surveillance.FoundPersons(new byte[0]));
        return null;
      });
    //Wait Timeout
    detector.expectMessageClass(GlobalCommands.Disarm.class, Duration.ofSeconds(11));
    LoggingTestKit.info("analyze").expect(testKit.system(), () -> {
      surveillance.tell(new Surveillance.FoundPersons(new byte[0]));
      return null;
    });
  }
}
