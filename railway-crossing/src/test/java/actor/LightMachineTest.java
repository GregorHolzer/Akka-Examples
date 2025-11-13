package actor;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import actors.light_machine.LightMachine;
import akka.actor.testkit.typed.javadsl.ActorTestKit;
import akka.actor.testkit.typed.javadsl.LoggingTestKit;
import akka.actor.testkit.typed.javadsl.TestKitJunitResource;
import akka.actor.typed.ActorRef;
import akka.persistence.testkit.javadsl.EventSourcedBehaviorTestKit;
import akka.persistence.testkit.javadsl.EventSourcedBehaviorTestKit.CommandResult;
import akka.persistence.typed.PersistenceId;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import service.RailwayService;

public class LightMachineTest {

  private final RailwayService mockedService = mock(RailwayService.class);

    static final ActorTestKit testKit = ActorTestKit.create();


    @Test
  public void fullLightMachineTest() {
        ActorRef<LightMachine.LightMachineCommand> lightMachine = testKit.spawn(LightMachine.create(mockedService), "lightMachine");
        LoggingTestKit.info("lightMachine in state On")
                .expect(
                        testKit.system(),
                        () -> {
                            lightMachine.tell(new LightMachine.CommandTurnOn());
                            return null;
                        }
                );
        verify(mockedService).lightOn(any(), any());
        LoggingTestKit.info("lightMachine in state Off")
                .expect(
                        testKit.system(),
                        () -> {
                            lightMachine.tell(new LightMachine.CommandTurnOff());
                            return null;
                        }
                );
        verify(mockedService).lightOff(any(), any());
  }

  @Test
  public void duplicateCommands() {
      ActorRef<LightMachine.LightMachineCommand> lightMachine = testKit.spawn(LightMachine.create(mockedService), "lightMachine1");
      lightMachine.tell(new LightMachine.CommandTurnOff());
      verify(mockedService, times(0)).lightOn(any(), any());
      verify(mockedService, times(0)).lightOff(any(), any());
      LoggingTestKit.info("lightMachine1 in state On")
              .expect(
                      testKit.system(),
                      () -> {
                          lightMachine.tell(new LightMachine.CommandTurnOn());
                          return null;
                      }
              );
      verify(mockedService).lightOn(any(), any());
      verify(mockedService, times(0)).lightOff(any(), any());
      lightMachine.tell(new LightMachine.CommandTurnOn());
      verify(mockedService).lightOn(any(), any());
      verify(mockedService, times(0)).lightOff(any(), any());
  }

}
