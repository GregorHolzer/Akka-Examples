package actor;

import actors.bell.Bell;
import actors.gate.Gate;
import actors.gate.GateState;
import actors.light_machine.LightMachine;
import akka.actor.testkit.typed.javadsl.TestKitJunitResource;
import akka.actor.testkit.typed.javadsl.TestProbe;
import akka.persistence.testkit.javadsl.EventSourcedBehaviorTestKit;
import akka.persistence.testkit.javadsl.EventSourcedBehaviorTestKit.CommandResult;
import akka.persistence.typed.PersistenceId;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import service.RailwayService;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;

public class GateTest {

    private final RailwayService mockedService = mock(RailwayService.class);

    @ClassRule
    public static final TestKitJunitResource testKit = new TestKitJunitResource(EventSourcedBehaviorTestKit.config());

    private final TestProbe<Bell.BellCommand> bell = testKit.createTestProbe(Bell.BellCommand.class);


    private final EventSourcedBehaviorTestKit<Gate.GateCommand, Gate.GateEvent, GateState> eventSourcedBehaviorTestKit =
            EventSourcedBehaviorTestKit.create(testKit.system(), Gate.create(PersistenceId.ofUniqueId("gate"), bell.getRef(), mockedService));

    @Before
    public void beforeEach() {
        eventSourcedBehaviorTestKit.clear();
    }

    @Test
    public void fullLightMachineTest(){
        assertEquals(GateState.State.OPEN,  eventSourcedBehaviorTestKit.getState().getState());

        CommandResult<Gate.GateCommand, Gate.GateEvent, GateState> result = eventSourcedBehaviorTestKit.runCommand(new Gate.GateCommandClose());
        assertEquals(2,  result.events().size());
        assertTrue(result.events().get(0) instanceof Gate.GateEventAdvanceState);
        assertTrue(result.events().get(1) instanceof Gate.GateEventClosed);
        assertEquals(GateState.State.CLOSED, eventSourcedBehaviorTestKit.getState().getState());

        result = eventSourcedBehaviorTestKit.runCommand(new Gate.GateCommandOpen());
        assertEquals(2,  result.events().size());
        assertTrue(result.events().get(0) instanceof Gate.GateEventAdvanceState);
        assertTrue(result.events().get(1) instanceof Gate.GateEventOpened);
        assertEquals(GateState.State.OPEN, eventSourcedBehaviorTestKit.getState().getState());
    }

    @Test
    public void duplicateCommands(){
        assertEquals(GateState.State.OPEN,  eventSourcedBehaviorTestKit.getState().getState());

        CommandResult<Gate.GateCommand, Gate.GateEvent, GateState> result = eventSourcedBehaviorTestKit.runCommand(new Gate.GateCommandOpen());
        assertTrue(result.events().isEmpty());
        assertEquals(GateState.State.OPEN, eventSourcedBehaviorTestKit.getState().getState());
    }
}