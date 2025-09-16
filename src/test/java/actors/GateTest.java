package actors;
import actors.messages.Approaching;
import actors.messages.ControllerMessage;

import actors.messages.Leaving;
import akka.actor.testkit.typed.javadsl.ActorTestKit;
import akka.actor.testkit.typed.javadsl.LoggingTestKit;
import akka.actor.typed.ActorRef;
import org.junit.jupiter.api.*;

class GateTest {

    private ActorTestKit testKit;

    @BeforeEach
    public void setupTestKit(){
        testKit = ActorTestKit.create();
    }

    @Test
    void testGate(){
        String name = "TestGate";
        ActorRef<ControllerMessage> gate = testKit.spawn(Gate.create(), name);
        LoggingTestKit.info(name + " is in State " + Gate.GateState.UP)
                .expect(testKit.system(), () -> {
                    gate.tell(new Leaving());
                    return null;
                });
        LoggingTestKit.info(name + " is in State " + Gate.GateState.DOWN)
                .expect(testKit.system(), () -> {
                    gate.tell(new Approaching());
                    return null;
                });
        LoggingTestKit.info(name + " is in State " + Gate.GateState.UP)
                .expect(testKit.system(), () -> {
                    gate.tell(new Leaving());
                    return null;
                });
    }

    @AfterEach
    public void resetTestKit(){
        testKit.shutdownTestKit();
    }

}
