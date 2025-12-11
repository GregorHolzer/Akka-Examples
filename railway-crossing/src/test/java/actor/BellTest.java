package actor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import actors.common.NodeConfig;
import actors.Bell;
import akka.actor.testkit.typed.javadsl.ActorTestKit;
import akka.actor.testkit.typed.javadsl.LoggingTestKit;
import akka.actor.typed.ActorRef;
import actors.common.Telemetry;
import org.junit.AfterClass;
import org.junit.Test;
import service.RailwayService;

public class BellTest {

  private static final NodeConfig nodeConfig = new NodeConfig(
    null,
    null,
    null,
    null,
    1,
    "localhost",
    4317
  );

  private static final Double trainSpeed = 50.0;

  private static final String traceId = "trace1";

  private static final String spanId = "span1";

  private final RailwayService mockedService = mock(RailwayService.class);

  static final ActorTestKit testKit = ActorTestKit.create();

  @Test
  public void fullBellTest() {
    Telemetry.setupOpenTelemetry(nodeConfig);
    ActorRef<Bell.BellCommand> bell = testKit.spawn(Bell.create(mockedService), "bell");
    LoggingTestKit.info("bell in state On").expect(testKit.system(), () -> {
      bell.tell(new Bell.CommandBellOn(trainSpeed));
      return null;
    });
    verify(mockedService).bellOn(any(), any(), any());
    LoggingTestKit.info("bell in state Off").expect(testKit.system(), () -> {
      bell.tell(new Bell.CommandBellOff(traceId, spanId));
      return null;
    });
    verify(mockedService).bellOff(any(), any(), any(), any());
  }

  @Test
  public void duplicateCommands() {
    ActorRef<Bell.BellCommand> bell = testKit.spawn(Bell.create(mockedService), "bell1");
    LoggingTestKit.info("bell1 in state")
      .withOccurrences(0)
      .expect(testKit.system(), () -> {
        bell.tell(new Bell.CommandBellOff(traceId, spanId));
        return null;
      });
    verify(mockedService, times(0)).bellOn(any(), any(), any());
    verify(mockedService, times(0)).bellOff(any(), any(), any(), any());
    LoggingTestKit.info("bell1 in state On").expect(testKit.system(), () -> {
      bell.tell(new Bell.CommandBellOn(trainSpeed));
      return null;
    });
    verify(mockedService, times(1)).bellOn(any(), any(), any());
    verify(mockedService, times(0)).bellOff(any(), any(), any(), any());
    LoggingTestKit.info("bell1 in state")
      .withOccurrences(0)
      .expect(testKit.system(), () -> {
        bell.tell(new Bell.CommandBellOn(trainSpeed));
        return null;
      });
    verify(mockedService, times(1)).bellOn(any(), any(), any());
    verify(mockedService, times(0)).bellOff(any(), any(), any(), any());
  }

  @AfterClass
  public static void cleanUp() {
    testKit.shutdownTestKit();
  }
}
