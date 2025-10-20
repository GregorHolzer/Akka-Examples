package actor;

import actors.controller.Controller;
import actors.controller.ControllerState;
import actors.controller.commands.ControllerCommand;
import actors.controller.commands.ControllerCommandTrainNotSeen;
import actors.controller.commands.ControllerCommandTrainSeen;
import actors.controller.events.ControllerEvent;
import actors.controller.events.ControllerEventAdvanceState;
import actors.controller.events.ControllerEventRaiseApproaching;
import actors.controller.events.ControllerEventRaiseLeaving;
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

    private final EventSourcedBehaviorTestKit<ControllerCommand, ControllerEvent, ControllerState> eventSourcedBehaviorTestKit =
            EventSourcedBehaviorTestKit.create(testKit.system(), Controller.create(PersistenceId.of("Controller", "1")));

    @Before
    public void beforeEach() {
        eventSourcedBehaviorTestKit.clear();
    }

    @Test
    public void fullControllerTest(){
        assertEquals(ControllerState.State.AWAY, eventSourcedBehaviorTestKit.getState().getState());

        CommandResult<ControllerCommand, ControllerEvent, ControllerState> result = eventSourcedBehaviorTestKit.runCommand(new ControllerCommandTrainSeen());
        assertEquals(2, result.events().size());
        assertTrue(result.events().get(0) instanceof ControllerEventAdvanceState);
        assertTrue(result.events().get(1) instanceof ControllerEventRaiseApproaching);
        assertEquals(ControllerState.State.APPROACHING, eventSourcedBehaviorTestKit.getState().getState());

        result = eventSourcedBehaviorTestKit.runCommand(new ControllerCommandTrainNotSeen());
        assertTrue(result.event() instanceof ControllerEventAdvanceState);
        assertEquals(ControllerState.State.CLOSE, eventSourcedBehaviorTestKit.getState().getState());

        result = eventSourcedBehaviorTestKit.runCommand(new ControllerCommandTrainSeen());
        assertTrue(result.event() instanceof ControllerEventAdvanceState);
        assertEquals(ControllerState.State.PRESENT, eventSourcedBehaviorTestKit.getState().getState());

        result = eventSourcedBehaviorTestKit.runCommand(new ControllerCommandTrainNotSeen());
        assertEquals(2, result.events().size());
        assertTrue(result.events().get(0) instanceof ControllerEventAdvanceState);
        assertTrue(result.events().get(1) instanceof ControllerEventRaiseLeaving);
        assertEquals(ControllerState.State.LEAVING, eventSourcedBehaviorTestKit.getState().getState());

        result = eventSourcedBehaviorTestKit.runCommand(new ControllerCommandTrainSeen());
        assertTrue(result.event() instanceof ControllerEventAdvanceState);
        assertEquals(ControllerState.State.LEFT, eventSourcedBehaviorTestKit.getState().getState());

        result = eventSourcedBehaviorTestKit.runCommand(new ControllerCommandTrainNotSeen());
        assertTrue(result.event() instanceof ControllerEventAdvanceState);
        assertEquals(ControllerState.State.AWAY, eventSourcedBehaviorTestKit.getState().getState());
    }

    @Test
    public void duplicateCommands(){
        assertEquals(ControllerState.State.AWAY, eventSourcedBehaviorTestKit.getState().getState());

        CommandResult<ControllerCommand, ControllerEvent, ControllerState> result = eventSourcedBehaviorTestKit.runCommand(new ControllerCommandTrainNotSeen());
        assertTrue(result.events().isEmpty());
        assertEquals(ControllerState.State.AWAY, eventSourcedBehaviorTestKit.getState().getState());
    }
}
