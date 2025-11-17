package actor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import actors.bell.Bell;
import actors.gate.Gate;
import akka.actor.testkit.typed.javadsl.ActorTestKit;
import akka.actor.testkit.typed.javadsl.LoggingTestKit;
import akka.actor.testkit.typed.javadsl.TestProbe;
import akka.actor.typed.ActorRef;
import org.junit.AfterClass;
import org.junit.Test;
import service.RailwayService;

public class GateTest {

  private final RailwayService mockedService = mock(RailwayService.class);

  private static final ActorTestKit testKit = ActorTestKit.create();

  private final TestProbe<Bell.BellCommand> bell = testKit.createTestProbe(Bell.BellCommand.class);

  @Test
  public void fullLightMachineTest() {
    ActorRef<Gate.GateCommand> gate = testKit.spawn(
      Gate.create(bell.getRef(), mockedService),
      "gate"
    );

    LoggingTestKit.info("gate in state Closed").expect(testKit.system(), () -> {
      gate.tell(new Gate.GateCommandClose());
      return null;
    });
    bell.expectMessageClass(Bell.CommandBellOn.class);
    verify(mockedService).gateDown(any(), any());
    verify(mockedService, times(0)).gateUp(any(), any());
    LoggingTestKit.info("gate in state Open").expect(testKit.system(), () -> {
      gate.tell(new Gate.GateCommandOpen());
      return null;
    });
    bell.expectMessageClass(Bell.CommandBellOff.class);
    verify(mockedService).gateDown(any(), any());
    verify(mockedService).gateUp(any(), any());
  }

  @Test
  public void duplicateCommands() {
    ActorRef<Gate.GateCommand> gate = testKit.spawn(
      Gate.create(bell.getRef(), mockedService),
      "gate1"
    );
    LoggingTestKit.info("gate1 in state ")
      .withOccurrences(0)
      .expect(testKit.system(), () -> {
        gate.tell(new Gate.GateCommandOpen());
        return null;
      });
    verify(mockedService, times(0)).gateDown(any(), any());
    verify(mockedService, times(0)).gateUp(any(), any());
    LoggingTestKit.info("gate1 in state ").expect(testKit.system(), () -> {
      gate.tell(new Gate.GateCommandClose());
      return null;
    });
    bell.expectMessageClass(Bell.CommandBellOn.class);
    verify(mockedService).gateDown(any(), any());
    verify(mockedService, times(0)).gateUp(any(), any());
    LoggingTestKit.info("gate1 in state ")
      .withOccurrences(0)
      .expect(testKit.system(), () -> {
        gate.tell(new Gate.GateCommandClose());
        return null;
      });
    verify(mockedService).gateDown(any(), any());
    verify(mockedService, times(0)).gateUp(any(), any());
  }

  @AfterClass
  public static void cleanUp() {
    testKit.shutdownTestKit();
  }
}
