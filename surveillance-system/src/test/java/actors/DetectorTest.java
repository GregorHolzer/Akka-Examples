package actors;

import actors.Detector.Detector;
import actors.Detector.DetectorState;
import akka.actor.testkit.typed.javadsl.TestKitJunitResource;
import akka.persistence.testkit.javadsl.EventSourcedBehaviorTestKit;
import akka.persistence.testkit.javadsl.EventSourcedBehaviorTestKit.CommandResult;
import akka.persistence.typed.PersistenceId;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import static org.junit.Assert.*;

public class DetectorTest {

    @ClassRule
    public static final TestKitJunitResource testKit = new TestKitJunitResource(EventSourcedBehaviorTestKit.config());

    private final EventSourcedBehaviorTestKit<Detector.DetectorCommand, Detector.DetectorEvent, DetectorState> eventSourcedBehaviorTestKit =
            EventSourcedBehaviorTestKit.create(testKit.system(), Detector.create(PersistenceId.ofUniqueId("detector")));

    @Before
    public void beforeEach() {
        eventSourcedBehaviorTestKit.clear();
    }

    @Test
    public void detectorTestTimeout(){
        assertEquals(DetectorState.State.Capturing, eventSourcedBehaviorTestKit.getState().getState());

        CommandResult<Detector.DetectorCommand, Detector.DetectorEvent, DetectorState> result = eventSourcedBehaviorTestKit.runCommand(new Detector.CommandCaptured());
        assertTrue(result.event() instanceof Detector.EventCaptured);
        assertEquals(DetectorState.State.Processing, eventSourcedBehaviorTestKit.getState().getState());

        result = eventSourcedBehaviorTestKit.runCommand(new Detector.CommandTimeout());
        assertTrue(result.event() instanceof Detector.EventTimeout);
        assertEquals(DetectorState.State.Capturing,  eventSourcedBehaviorTestKit.getState().getState());
    }

    @Test
    public void detectorTestAlarm(){
        assertEquals(DetectorState.State.Capturing, eventSourcedBehaviorTestKit.getState().getState());

        CommandResult<Detector.DetectorCommand, Detector.DetectorEvent, DetectorState> result = eventSourcedBehaviorTestKit.runCommand(new Detector.CommandCaptured());
        assertTrue(result.event() instanceof Detector.EventCaptured);
        assertEquals(DetectorState.State.Processing, eventSourcedBehaviorTestKit.getState().getState());

        result = eventSourcedBehaviorTestKit.runCommand(new Detector.CommandAlarm());
        assertTrue(result.event() instanceof Detector.EventAlarm);
        assertEquals(DetectorState.State.Alarm,  eventSourcedBehaviorTestKit.getState().getState());

        result = eventSourcedBehaviorTestKit.runCommand(new Detector.CommandDisarm());
        assertTrue(result.event() instanceof Detector.EventDisarm);
        assertEquals(DetectorState.State.Capturing,  eventSourcedBehaviorTestKit.getState().getState());
    }

    @Test
    public void detectorTestWithSurveillance(){
        assertEquals(DetectorState.State.Capturing, eventSourcedBehaviorTestKit.getState().getState());

        CommandResult<Detector.DetectorCommand, Detector.DetectorEvent, DetectorState> result = eventSourcedBehaviorTestKit.runCommand(new Detector.CommandCaptured());
        assertTrue(result.event() instanceof Detector.EventCaptured);
        assertEquals(DetectorState.State.Processing, eventSourcedBehaviorTestKit.getState().getState());

    }
}
