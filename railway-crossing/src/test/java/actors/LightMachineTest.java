package actors;

import actors.messages.Approaching;
import actors.messages.ControllerMessage;
import actors.messages.Leaving;
import akka.actor.testkit.typed.javadsl.ActorTestKit;
import akka.actor.testkit.typed.javadsl.LoggingTestKit;
import akka.actor.typed.ActorRef;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

class LightMachineTest {

    private ActorTestKit testKit;

    @BeforeEach
    public void setupTestKit() {
        testKit = ActorTestKit.create();
    }

    @Test
    void testLightMachine() {
        String name = "TestLightMachine";
        ActorRef<ControllerMessage> lightMachine = testKit.spawn(LightMachine.create(), name);
        LoggingTestKit.info(name + " is in State " + LightMachine.LightMachineState.OFF)
                .expect(testKit.system(), () -> {
                   lightMachine.tell(new Leaving());
                   return null;
                });
        LoggingTestKit.info(name + " is in State " + LightMachine.LightMachineState.ON)
                .expect(testKit.system(), () -> {
                    lightMachine.tell(new Approaching());
                    return null;
                });
        LoggingTestKit.info(name + " is in State " + LightMachine.LightMachineState.OFF)
                .expect(testKit.system(), () -> {
                    lightMachine.tell(new Leaving());
                    return null;
                });
    }

    @AfterEach
    public void resetTestKit() {
        testKit.shutdownTestKit();
    }
}
