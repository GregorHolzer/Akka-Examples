package actors;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import actors.common.SharedCommands;
import akka.actor.testkit.typed.javadsl.ActorTestKit;
import akka.actor.testkit.typed.javadsl.LoggingTestKit;
import akka.actor.testkit.typed.javadsl.TestProbe;
import akka.actor.typed.ActorRef;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.pubsub.PubSub;
import akka.actor.typed.pubsub.Topic;
import java.time.Duration;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;
import services.SurveillanceService;

public class SurveillanceTest {

  private final SurveillanceService surveillanceService = mock(
    SurveillanceService.class
  );

  static final ActorTestKit testKit = ActorTestKit.create();

  private final TestProbe<Detector.DetectorCommand> detector =
    testKit.createTestProbe("detector", Detector.DetectorCommand.class);

  @Before
  public void setup() {
    doAnswer(invocationOnMock -> {
      ActorContext<Detector.DetectorCommand> context =
        invocationOnMock.getArgument(0);
      context.getLog().info("analyze");
      return null;
    })
      .when(surveillanceService)
      .analyze(any(), any());
    PubSub pubSub = PubSub.get(testKit.system());
    ActorRef<Topic.Command<Detector.DetectorCommand>> detectorTopic =
      pubSub.topic(Detector.DetectorCommand.class, "global-detector-commands");
    detectorTopic.tell(Topic.subscribe(detector.getRef()));
  }

  @Test
  public void surveillanceTestNoAlarm() {
    ActorRef<Surveillance.SurveillanceCommand> surveillance = testKit.spawn(
      Surveillance.create(surveillanceService, "test1", 1500),
      "test1"
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
    testKit.stop(surveillance);
  }

  @Test
  public void surveillanceTestAlarmManualDisarm() {
    ActorRef<Surveillance.SurveillanceCommand> surveillance = testKit.spawn(
      Surveillance.create(surveillanceService, "test2", 1500),
      "test2"
    );

    LoggingTestKit.info("analyze").expect(testKit.system(), () -> {
      surveillance.tell(new Surveillance.FoundPersons(new byte[0]));
      return null;
    });

    LoggingTestKit.info("in state Alarm").expect(testKit.system(), () -> {
      surveillance.tell(new Surveillance.Analyzed(new byte[0], true));
      return null;
    });
    detector.expectMessageClass(SharedCommands.Alarm.class);

    LoggingTestKit.info("in state Processing").expect(testKit.system(), () -> {
      surveillance.tell(new SharedCommands.Disarm());
      return null;
    });
    LoggingTestKit.info("analyze").expect(testKit.system(), () -> {
      surveillance.tell(new Surveillance.FoundPersons(new byte[0]));
      return null;
    });
    testKit.stop(surveillance);
  }

  @Test
  public void surveillanceTestAlarmTimeoutDisarm() {
    ActorRef<Surveillance.SurveillanceCommand> surveillance = testKit.spawn(
      Surveillance.create(surveillanceService, "test3", 1500),
      "test3"
    );
    LoggingTestKit.info("in state Alarm").expect(testKit.system(), () -> {
      surveillance.tell(new SharedCommands.Alarm());
      return null;
    });
    LoggingTestKit.info("analyze")
      .withOccurrences(0)
      .expect(testKit.system(), () -> {
        surveillance.tell(new Surveillance.FoundPersons(new byte[0]));
        return null;
      });
    //Wait Timeout
    detector.expectMessageClass(
      SharedCommands.Disarm.class,
      Duration.ofSeconds(11)
    );
    LoggingTestKit.info("analyze").expect(testKit.system(), () -> {
      surveillance.tell(new Surveillance.FoundPersons(new byte[0]));
      return null;
    });
    testKit.stop(surveillance);
  }

  @AfterClass
  public static void cleanUp() {
    testKit.shutdownTestKit();
  }
}
