package actor;

import actors.bell.Bell;
import actors.bell.BellState;
import akka.actor.testkit.typed.javadsl.TestKitJunitResource;
import akka.persistence.testkit.javadsl.EventSourcedBehaviorTestKit;
import akka.persistence.testkit.javadsl.EventSourcedBehaviorTestKit.CommandResult;
import akka.persistence.typed.PersistenceId;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import service.RailwayService;

import static org.mockito.Mockito.*;
import static org.junit.Assert.*;

public class BellTest {

    private final RailwayService mockedService = mock(RailwayService.class);


    @ClassRule
    public static final TestKitJunitResource testKit = new TestKitJunitResource(EventSourcedBehaviorTestKit.config());

    private final EventSourcedBehaviorTestKit<Bell.BellCommand, Bell.BellEvent, BellState> eventSourcedBehaviorTestKit =
            EventSourcedBehaviorTestKit.create(testKit.system(), Bell.create(PersistenceId.ofUniqueId("bell"), mockedService));

    @Before
    public void beforeEach() {
        eventSourcedBehaviorTestKit.clear();
    }

    @Test
    public void fullBellTest(){
        assertEquals(BellState.State.OFF, eventSourcedBehaviorTestKit.getState().getState());

        CommandResult<Bell.BellCommand, Bell.BellEvent, BellState> result = eventSourcedBehaviorTestKit.runCommand(
                new Bell.CommandBellOn()
        );
        assertEquals(2, result.events().size());
        assertTrue(result.events().get(0) instanceof Bell.EventAdvanceState);
        assertTrue(result.events().get(1) instanceof Bell.EventBellOn);
        assertEquals(BellState.State.ON, eventSourcedBehaviorTestKit.getState().getState());

        result = eventSourcedBehaviorTestKit.runCommand(new Bell.CommandBellOff());
        assertEquals(2, result.events().size());
        assertTrue(result.events().get(0) instanceof Bell.EventAdvanceState);
        assertTrue(result.events().get(1) instanceof Bell.EventBellOff);
        assertEquals(BellState.State.OFF, eventSourcedBehaviorTestKit.getState().getState());
    }

    @Test
    public void duplicateCommands(){
        assertEquals(BellState.State.OFF, eventSourcedBehaviorTestKit.getState().getState());

        CommandResult<Bell.BellCommand, Bell.BellEvent, BellState> result = eventSourcedBehaviorTestKit.runCommand(
                new Bell.CommandBellOff()
        );
        assertTrue(result.events().isEmpty());
        assertEquals(BellState.State.OFF, eventSourcedBehaviorTestKit.getState().getState());
    }
}