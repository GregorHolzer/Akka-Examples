package actor;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import actors.bell.Bell;
import akka.actor.testkit.typed.javadsl.ActorTestKit;
import akka.actor.testkit.typed.javadsl.LoggingTestKit;
import akka.actor.typed.ActorRef;
import org.junit.AfterClass;
import org.junit.Test;
import service.RailwayService;

public class BellTest {

  private final RailwayService mockedService = mock(RailwayService.class);

  static final ActorTestKit testKit = ActorTestKit.create();


  @Test
  public void fullBellTest() {
      ActorRef<Bell.BellCommand> bell = testKit.spawn(Bell.create(mockedService), "bell");
      LoggingTestKit.info("bell in state On")
              .expect(
                      testKit.system(),
                      () -> {
                          bell.tell(new Bell.CommandBellOn());
                          return null;
                      }
              );
      verify(mockedService).bellOn(any(), any());
      LoggingTestKit.info("bell in state Off")
              .expect(
                      testKit.system(),
                      () -> {
                          bell.tell(new Bell.CommandBellOff());
                          return null;
                      }
              );
      verify(mockedService).bellOff(any(), any());
  }

  @Test
  public void duplicateCommands() {
      ActorRef<Bell.BellCommand> bell = testKit.spawn(Bell.create(mockedService), "bell1");
      bell.tell(new Bell.CommandBellOff());
      verify(mockedService, times(0)).bellOn(any(), any());
      verify(mockedService, times(0)).bellOff(any(), any());
      LoggingTestKit.info("bell1 in state On")
              .expect(
                      testKit.system(),
                      () -> {
                          bell.tell(new Bell.CommandBellOn());
                          return null;
                      }
              );
      verify(mockedService, times(1)).bellOn(any(), any());
      verify(mockedService, times(0)).bellOff(any(), any());
      bell.tell(new Bell.CommandBellOn());
      verify(mockedService, times(1)).bellOn(any(), any());
      verify(mockedService, times(0)).bellOff(any(), any());
  }

  @AfterClass
  public static void cleanUp(){
      testKit.shutdownTestKit();
  }
}
