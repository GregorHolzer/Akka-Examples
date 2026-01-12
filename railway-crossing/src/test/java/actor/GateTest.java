package actor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import actors.Bell;
import actors.Gate;
import actors.common.RailwayService;
import actors.common.Telemetry;
import akka.actor.testkit.typed.javadsl.ActorTestKit;
import akka.actor.testkit.typed.javadsl.LoggingTestKit;
import akka.actor.testkit.typed.javadsl.TestProbe;
import akka.actor.typed.ActorRef;
import org.junit.AfterClass;
import org.junit.Test;

public class GateTest {

  private static final String ANY_LOG = "in state";

  private static final Double trainSpeed = 50.0;

  private static final String traceId = "trace1";

  private static final String spanId = "span1";

  private final RailwayService mockedService = mock(RailwayService.class);

  private static final ActorTestKit testKit = ActorTestKit.create();

  private final TestProbe<Bell.BellCommand> bell = testKit.createTestProbe(
    Bell.BellCommand.class
  );

  @Test
  public void fullLightMachineTest() {
    Telemetry.initOpenTelemetry();
    ActorRef<Gate.GateCommand> gate = testKit.spawn(
      Gate.create(bell.getRef(), mockedService),
      "gate"
    );
    LoggingTestKit.info("gate in state Closed").expect(testKit.system(), () -> {
      gate.tell(new Gate.CommandClose(trainSpeed));
      return null;
    });
    bell.expectMessageClass(Bell.CommandBellOn.class);
    verify(mockedService).gateDown(any(), any(), any());
    verify(mockedService, times(0)).gateUp(any(), any(), any(), any(), any());
    LoggingTestKit.info("gate in state Open").expect(testKit.system(), () -> {
      gate.tell(new Gate.CommandOpen(traceId, spanId));
      return null;
    });
    verify(mockedService).gateDown(any(), any(), any());
    verify(mockedService).gateUp(any(), any(), any(), any(), any());
  }

  @Test
  public void duplicateCommands() {
    ActorRef<Gate.GateCommand> gate = testKit.spawn(
      Gate.create(bell.getRef(), mockedService),
      "gate1"
    );
    LoggingTestKit.info(ANY_LOG)
      .withOccurrences(0)
      .expect(testKit.system(), () -> {
        gate.tell(new Gate.CommandOpen(traceId, spanId));
        return null;
      });
    verify(mockedService, times(0)).gateDown(any(), any(), any());
    verify(mockedService, times(0)).gateUp(any(), any(), any(), any(), any());
    LoggingTestKit.info("gate1 in state Closed").expect(
      testKit.system(),
      () -> {
        gate.tell(new Gate.CommandClose(trainSpeed));
        return null;
      }
    );
    bell.expectMessageClass(Bell.CommandBellOn.class);
    verify(mockedService).gateDown(any(), any(), any());
    verify(mockedService, times(0)).gateUp(any(), any(), any(), any(), any());
    LoggingTestKit.info(ANY_LOG)
      .withOccurrences(0)
      .expect(testKit.system(), () -> {
        gate.tell(new Gate.CommandClose(trainSpeed));
        return null;
      });
    verify(mockedService).gateDown(any(), any(), any());
    verify(mockedService, times(0)).gateUp(any(), any(), any(), any(), any());
  }

  @AfterClass
  public static void cleanUp() {
    testKit.shutdownTestKit();
  }
}
