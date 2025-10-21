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

        CommandResult<Controller.ControllerCommand, Controller.ControllerEvent, ControllerState> result = eventSourcedBehaviorTestKit.runCommand(new Controller.TrainSeen());
        assertEquals(2, result.events().size());
        assertTrue(result.events().get(0) instanceof Controller.AdvanceState);
        assertTrue(result.events().get(1) instanceof Controller.RaiseApproaching);
        assertEquals(ControllerState.State.APPROACHING, eventSourcedBehaviorTestKit.getState().getState());

        result = eventSourcedBehaviorTestKit.runCommand(new Controller.TrainNotSeen());
        assertTrue(result.event() instanceof Controller.AdvanceState);
        assertEquals(ControllerState.State.CLOSE, eventSourcedBehaviorTestKit.getState().getState());

        result = eventSourcedBehaviorTestKit.runCommand(new Controller.TrainSeen());
        assertTrue(result.event() instanceof Controller.AdvanceState);
        assertEquals(ControllerState.State.PRESENT, eventSourcedBehaviorTestKit.getState().getState());

        result = eventSourcedBehaviorTestKit.runCommand(new Controller.TrainNotSeen());
        assertEquals(2, result.events().size());
        assertTrue(result.events().get(0) instanceof Controller.AdvanceState);
        assertTrue(result.events().get(1) instanceof Controller.RaiseLeaving);
        assertEquals(ControllerState.State.LEAVING, eventSourcedBehaviorTestKit.getState().getState());

        result = eventSourcedBehaviorTestKit.runCommand(new Controller.TrainSeen());
        assertTrue(result.event() instanceof Controller.AdvanceState);
        assertEquals(ControllerState.State.LEFT, eventSourcedBehaviorTestKit.getState().getState());

        result = eventSourcedBehaviorTestKit.runCommand(new Controller.TrainNotSeen());
        assertTrue(result.event() instanceof Controller.AdvanceState);
        assertEquals(ControllerState.State.AWAY, eventSourcedBehaviorTestKit.getState().getState());
    }

    @Test
    public void duplicateCommands(){
        assertEquals(ControllerState.State.AWAY, eventSourcedBehaviorTestKit.getState().getState());
        CommandResult<Controller.ControllerCommand, Controller.ControllerEvent, ControllerState> result = eventSourcedBehaviorTestKit.runCommand(new Controller.TrainNotSeen());
        assertTrue(result.events().isEmpty());
        assertEquals(ControllerState.State.AWAY, eventSourcedBehaviorTestKit.getState().getState());
    }
}