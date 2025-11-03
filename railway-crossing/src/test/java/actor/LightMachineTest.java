package actor;

import actors.light_machine.LightMachine;
import actors.light_machine.LightMachineState;
import akka.actor.testkit.typed.javadsl.TestKitJunitResource;
import akka.persistence.testkit.javadsl.EventSourcedBehaviorTestKit;
import akka.persistence.testkit.javadsl.EventSourcedBehaviorTestKit.CommandResult;
import akka.persistence.typed.PersistenceId;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import service.RailwayService;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;

public class LightMachineTest {

    private final RailwayService mockedService = mock(RailwayService.class);

    @ClassRule
    public static final TestKitJunitResource testKit = new TestKitJunitResource(EventSourcedBehaviorTestKit.config());

    private final EventSourcedBehaviorTestKit<LightMachine.LightMachineCommand, LightMachine.LightMachineEvent, LightMachineState> eventSourcedBehaviorTestKit =
            EventSourcedBehaviorTestKit.create(testKit.system(), LightMachine.create(PersistenceId.ofUniqueId("light_machine"), mockedService));

    @Before
    public void beforeEach() {
        eventSourcedBehaviorTestKit.clear();
    }

    @Test
    public void fullLightMachineTest(){
        assertEquals(LightMachineState.State.OFF,  eventSourcedBehaviorTestKit.getState().getState());

        CommandResult<LightMachine.LightMachineCommand, LightMachine.LightMachineEvent, LightMachineState> result = eventSourcedBehaviorTestKit.runCommand(new LightMachine.CommandTurnOn());
        assertEquals(2, result.events().size());
        assertTrue(result.events().get(0) instanceof LightMachine.EventAdvanceState);
        assertTrue(result.events().get(1) instanceof LightMachine.EventTurnedOn);
        assertEquals(LightMachineState.State.ON, eventSourcedBehaviorTestKit.getState().getState());

        result = eventSourcedBehaviorTestKit.runCommand(new LightMachine.CommandTurnOff());
        assertEquals(2, result.events().size());
        assertTrue(result.events().get(0) instanceof LightMachine.EventAdvanceState);
        assertTrue(result.events().get(1) instanceof LightMachine.EventTurnedOff);
        assertEquals(LightMachineState.State.OFF, eventSourcedBehaviorTestKit.getState().getState());
    }

    @Test
    public void duplicateCommands(){
        assertEquals(LightMachineState.State.OFF,  eventSourcedBehaviorTestKit.getState().getState());
        CommandResult<LightMachine.LightMachineCommand, LightMachine.LightMachineEvent, LightMachineState> result = eventSourcedBehaviorTestKit.runCommand(new LightMachine.CommandTurnOff());
        assertTrue(result.events().isEmpty());
        assertEquals(LightMachineState.State.OFF, eventSourcedBehaviorTestKit.getState().getState());
    }
}