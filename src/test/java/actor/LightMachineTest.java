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

import static org.junit.Assert.*;

public class LightMachineTest {

    @ClassRule
    public static final TestKitJunitResource testKit = new TestKitJunitResource(EventSourcedBehaviorTestKit.config());

    private final EventSourcedBehaviorTestKit<LightMachine.LightMachineCommand, LightMachine.LightMachineEvent, LightMachineState> eventSourcedBehaviorTestKit =
            EventSourcedBehaviorTestKit.create(testKit.system(), LightMachine.create(PersistenceId.ofUniqueId("light_machine")));

    @Before
    public void beforeEach() {
        eventSourcedBehaviorTestKit.clear();
    }

    @Test
    public void fullLightMachineTest(){
        assertEquals(LightMachineState.State.OFF,  eventSourcedBehaviorTestKit.getState().getState());

        CommandResult<LightMachine.LightMachineCommand, LightMachine.LightMachineEvent, LightMachineState> result = eventSourcedBehaviorTestKit.runCommand(new LightMachine.TurnOn());
        assertEquals(2, result.events().size());
        assertTrue(result.events().get(0) instanceof LightMachine.AdvanceState);
        assertTrue(result.events().get(1) instanceof LightMachine.TurnedOn);
        assertEquals(LightMachineState.State.ON, eventSourcedBehaviorTestKit.getState().getState());

        result = eventSourcedBehaviorTestKit.runCommand(new LightMachine.TurnOff());
        assertEquals(2, result.events().size());
        assertTrue(result.events().get(0) instanceof LightMachine.AdvanceState);
        assertTrue(result.events().get(1) instanceof LightMachine.TurnedOff);
        assertEquals(LightMachineState.State.OFF, eventSourcedBehaviorTestKit.getState().getState());
    }

    @Test
    public void duplicateCommands(){
        assertEquals(LightMachineState.State.OFF,  eventSourcedBehaviorTestKit.getState().getState());
        CommandResult<LightMachine.LightMachineCommand, LightMachine.LightMachineEvent, LightMachineState> result = eventSourcedBehaviorTestKit.runCommand(new LightMachine.TurnOff());
        assertTrue(result.events().isEmpty());
        assertEquals(LightMachineState.State.OFF, eventSourcedBehaviorTestKit.getState().getState());
    }
}