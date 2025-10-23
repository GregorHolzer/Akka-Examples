package actors;

import actors.Detector.Detector;
import actors.Detector.DetectorState;
import actors.Surveillance.Surveillance;
import actors.Surveillance.SurveillanceState;
import akka.actor.testkit.typed.javadsl.TestKitJunitResource;
import akka.actor.testkit.typed.javadsl.TestProbe;
import akka.persistence.testkit.javadsl.EventSourcedBehaviorTestKit;
import akka.persistence.testkit.javadsl.EventSourcedBehaviorTestKit.CommandResult;
import akka.persistence.typed.PersistenceId;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import static org.junit.Assert.*;

public class SurveillanceTest {

    @ClassRule
    public static final TestKitJunitResource testKit = new TestKitJunitResource(EventSourcedBehaviorTestKit.config());

    private final TestProbe<Detector.DetectorCommand> probe = testKit.createTestProbe(Detector.DetectorCommand.class);

    private final EventSourcedBehaviorTestKit<Surveillance.SurveillanceCommand, Surveillance.SurveillanceEvent, SurveillanceState> eventSourcedBehaviorTestKit =
            EventSourcedBehaviorTestKit.create(testKit.system(), Surveillance.create(PersistenceId.ofUniqueId("surveillance"), probe.ref()));

    @Before
    public void beforeEach() {
        eventSourcedBehaviorTestKit.clear();
    }

    @Test
    public void surveillanceTestNoAlarm() {
        assertEquals(SurveillanceState.State.Analyzing, eventSourcedBehaviorTestKit.getState().getState());

        CommandResult<Surveillance.SurveillanceCommand, Surveillance.SurveillanceEvent, SurveillanceState> result = eventSourcedBehaviorTestKit.runCommand(new Surveillance.CommandFoundPerson());
        assertTrue(result.event() instanceof Surveillance.EventFoundPerson);
        assertEquals(SurveillanceState.State.Analyzing, eventSourcedBehaviorTestKit.getState().getState());

        result = eventSourcedBehaviorTestKit.runCommand(new Surveillance.CommandAnalyzed(false));
        assertTrue(result.event() instanceof Surveillance.EventAnalyzed);
        assertEquals(SurveillanceState.State.Analyzing, eventSourcedBehaviorTestKit.getState().getState());
    }

    @Test
    public void surveillanceTestAlarm() {
        assertEquals(SurveillanceState.State.Analyzing, eventSourcedBehaviorTestKit.getState().getState());

        CommandResult<Surveillance.SurveillanceCommand, Surveillance.SurveillanceEvent, SurveillanceState> result = eventSourcedBehaviorTestKit.runCommand(new Surveillance.CommandFoundPerson());
        assertTrue(result.event() instanceof Surveillance.EventFoundPerson);
        assertEquals(SurveillanceState.State.Analyzing, eventSourcedBehaviorTestKit.getState().getState());

        result = eventSourcedBehaviorTestKit.runCommand(new Surveillance.CommandAnalyzed(true));
        probe.expectMessageClass(Detector.CommandAlarm.class);
        assertTrue(result.event() instanceof Surveillance.EventAnalyzed);
        assertEquals(SurveillanceState.State.Alarm, eventSourcedBehaviorTestKit.getState().getState());

        result = eventSourcedBehaviorTestKit.runCommand(new Surveillance.CommandDisarm());
        assertTrue(result.event() instanceof Surveillance.EventDisarm);
        assertEquals(SurveillanceState.State.Analyzing, eventSourcedBehaviorTestKit.getState().getState());
    }
}

