package actor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import actors.light_machine.LightMachine;
import akka.actor.testkit.typed.javadsl.ActorTestKit;
import akka.actor.testkit.typed.javadsl.LoggingTestKit;
import akka.actor.typed.ActorRef;
import org.junit.AfterClass;
import org.junit.Test;
import service.RailwayService;

public class LightMachineTest {

  private static final Double trainSpeed = 50.0;

  private final RailwayService mockedService = mock(RailwayService.class);

  static final ActorTestKit testKit = ActorTestKit.create();

  @Test
  public void fullLightMachineTest() {
    ActorRef<LightMachine.LightMachineCommand> lightMachine = testKit.spawn(
      LightMachine.create(mockedService),
      "lightMachine"
    );
    LoggingTestKit.info("lightMachine in state On").expect(testKit.system(), () -> {
      lightMachine.tell(new LightMachine.CommandTurnOn(trainSpeed));
      return null;
    });
    verify(mockedService).lightOn(any(), any(), any());
    LoggingTestKit.info("lightMachine in state Off").expect(testKit.system(), () -> {
      lightMachine.tell(new LightMachine.CommandTurnOff());
      return null;
    });
    verify(mockedService).lightOff(any(), any());
  }

  @Test
  public void duplicateCommands() {
    ActorRef<LightMachine.LightMachineCommand> lightMachine = testKit.spawn(
      LightMachine.create(mockedService),
      "lightMachine1"
    );
    LoggingTestKit.info("lightMachine1 in state ")
      .withOccurrences(0)
      .expect(testKit.system(), () -> {
        lightMachine.tell(new LightMachine.CommandTurnOff());
        return null;
      });
    verify(mockedService, times(0)).lightOn(any(), any(), any());
    verify(mockedService, times(0)).lightOff(any(), any());
    LoggingTestKit.info("lightMachine1 in state On").expect(testKit.system(), () -> {
      lightMachine.tell(new LightMachine.CommandTurnOn(trainSpeed));
      return null;
    });
    verify(mockedService).lightOn(any(), any(), any());
    verify(mockedService, times(0)).lightOff(any(), any());
    LoggingTestKit.info("lightMachine1 in state ")
      .withOccurrences(0)
      .expect(testKit.system(), () -> {
        lightMachine.tell(new LightMachine.CommandTurnOn(trainSpeed));
        return null;
      });
    verify(mockedService).lightOn(any(), any(), any());
    verify(mockedService, times(0)).lightOff(any(), any());
  }

  @AfterClass
  public static void cleanUp() {
    testKit.shutdownTestKit();
  }
}
