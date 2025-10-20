package actor;

import actors.light_machine.LightMachine;
import actors.light_machine.LightMachineState;
import actors.light_machine.commands.LightMachineCommand;
import actors.light_machine.commands.LightMachineCommandTurnOff;
import actors.light_machine.commands.LightMachineCommandTurnOn;
import actors.light_machine.events.LightMachineEvent;
import actors.light_machine.events.LightMachineEventAdvanceState;
import actors.light_machine.events.LightMachineEventTurnOff;
import actors.light_machine.events.LightMachineEventTurnOn;
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

    private final EventSourcedBehaviorTestKit<LightMachineCommand, LightMachineEvent, LightMachineState> eventSourcedBehaviorTestKit =
            EventSourcedBehaviorTestKit.create(testKit.system(), LightMachine.create(PersistenceId.of("LightMachine", "1")));

    @Before
    public void beforeEach() {
        eventSourcedBehaviorTestKit.clear();
    }

    @Test
    public void fullLightMachineTest(){
        assertEquals(LightMachineState.State.OFF,  eventSourcedBehaviorTestKit.getState().getState());

        CommandResult<LightMachineCommand, LightMachineEvent, LightMachineState> result = eventSourcedBehaviorTestKit.runCommand(new LightMachineCommandTurnOn());
        assertEquals(2, result.events().size());
        assertTrue(result.events().get(0) instanceof LightMachineEventAdvanceState);
        assertTrue(result.events().get(1) instanceof LightMachineEventTurnOn);
        assertEquals(LightMachineState.State.ON, eventSourcedBehaviorTestKit.getState().getState());

        result = eventSourcedBehaviorTestKit.runCommand(new LightMachineCommandTurnOff());
        assertEquals(2, result.events().size());
        assertTrue(result.events().get(0) instanceof LightMachineEventAdvanceState);
        assertTrue(result.events().get(1) instanceof LightMachineEventTurnOff);
        assertEquals(LightMachineState.State.OFF, eventSourcedBehaviorTestKit.getState().getState());
    }

    @Test
    public void duplicateCommands(){
        assertEquals(LightMachineState.State.OFF,  eventSourcedBehaviorTestKit.getState().getState());
        CommandResult<LightMachineCommand, LightMachineEvent, LightMachineState> result = eventSourcedBehaviorTestKit.runCommand(new LightMachineCommandTurnOff());
        assertTrue(result.events().isEmpty());
        assertEquals(LightMachineState.State.OFF, eventSourcedBehaviorTestKit.getState().getState());

    }
}
