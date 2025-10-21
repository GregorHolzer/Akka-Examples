package actor;

import actors.controller.Controller;
import actors.controller.ControllerState;
import akka.actor.testkit.typed.javadsl.TestKitJunitResource;
import akka.persistence.testkit.javadsl.EventSourcedBehaviorTestKit;
import akka.persistence.typed.PersistenceId;
import akka.persistence.testkit.javadsl.EventSourcedBehaviorTestKit.CommandResult;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

import static org.junit.Assert.*;

public class ControllerTest {

    @ClassRule
    public static final TestKitJunitResource testKit = new TestKitJunitResource(EventSourcedBehaviorTestKit.config());

    private final EventSourcedBehaviorTestKit<Controller.ControllerCommand, Controller.ControllerEvent, ControllerState> eventSourcedBehaviorTestKit =
            EventSourcedBehaviorTestKit.create(testKit.system(), Controller.create(PersistenceId.ofUniqueId("1")));

    @Before
    public void beforeEach() {
        eventSourcedBehaviorTestKit.clear();
    }

    @Test
    public void initialState(){
        assertEquals(ControllerState.State.AWAY, eventSourcedBehaviorTestKit.getState().getState());

        CommandResult<Controller.ControllerCommand, Controller.ControllerEvent, ControllerState> result = eventSourcedBehaviorTestKit.runCommand(new Controller.CommandTrainSeen());
        assertEquals(2, result.events().size());
        assertTrue(result.events().get(0) instanceof Controller.EventAdvanceState);
        assertTrue(result.events().get(1) instanceof Controller.EventRaiseApproaching);
        assertEquals(ControllerState.State.APPROACHING, eventSourcedBehaviorTestKit.getState().getState());

        result = eventSourcedBehaviorTestKit.runCommand(new Controller.CommandTrainNotSeen());
        assertTrue(result.event() instanceof Controller.EventAdvanceState);
        assertEquals(ControllerState.State.CLOSE, eventSourcedBehaviorTestKit.getState().getState());

        result = eventSourcedBehaviorTestKit.runCommand(new Controller.CommandTrainSeen());
        assertTrue(result.event() instanceof Controller.EventAdvanceState);
        assertEquals(ControllerState.State.PRESENT, eventSourcedBehaviorTestKit.getState().getState());

        result = eventSourcedBehaviorTestKit.runCommand(new Controller.CommandTrainNotSeen());
        assertEquals(2, result.events().size());
        assertTrue(result.events().get(0) instanceof Controller.EventAdvanceState);
        assertTrue(result.events().get(1) instanceof Controller.EventRaiseLeaving);
        assertEquals(ControllerState.State.LEAVING, eventSourcedBehaviorTestKit.getState().getState());

        result = eventSourcedBehaviorTestKit.runCommand(new Controller.CommandTrainSeen());
        assertTrue(result.event() instanceof Controller.EventAdvanceState);
        assertEquals(ControllerState.State.LEFT, eventSourcedBehaviorTestKit.getState().getState());

        result = eventSourcedBehaviorTestKit.runCommand(new Controller.CommandTrainNotSeen());
        assertTrue(result.event() instanceof Controller.EventAdvanceState);
        assertEquals(ControllerState.State.AWAY, eventSourcedBehaviorTestKit.getState().getState());
    }

    @Test
    public void duplicateCommands(){
        assertEquals(ControllerState.State.AWAY, eventSourcedBehaviorTestKit.getState().getState());
        CommandResult<Controller.ControllerCommand, Controller.ControllerEvent, ControllerState> result = eventSourcedBehaviorTestKit.runCommand(new Controller.CommandTrainNotSeen());
        assertTrue(result.events().isEmpty());
        assertEquals(ControllerState.State.AWAY, eventSourcedBehaviorTestKit.getState().getState());
    }
}