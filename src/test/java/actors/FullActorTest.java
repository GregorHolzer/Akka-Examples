package actors;

import akka.actor.testkit.typed.LoggingEvent;
import akka.actor.testkit.typed.javadsl.ActorTestKit;
import akka.actor.testkit.typed.javadsl.LoggingTestKit;
import akka.actor.typed.ActorRef;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class FullActorTest {
    private ActorTestKit testKit;

    @BeforeEach
    public void setupTestKit(){
        testKit = ActorTestKit.create();
    }

    @Test
    void testGate(){
        String name = "TestController";
        ActorRef<Controller.SensorMessage> controller = testKit.spawn(Controller.create(), name);
        LoggingTestKit.info(name + " is in State " + Controller.ControllerState.AWAY)
                .expect(testKit.system(), () -> {
                    controller.tell(new Controller.TrainNotSeen());
                    return null;
                });

        LoggingTestKit.custom(msg ->  msg.message().contains(name + " is in State " + Controller.ControllerState.APPROACHING) ||
                        msg.message().contains(name + "-Gate" + " is in State " + Gate.GateState.DOWN) ||
                        msg.message().contains(name + "-LightMachine" + " is in State " + LightMachine.LightMachineState.ON))
                .withOccurrences(3)
                .expect(testKit.system(), () -> {
                    controller.tell(new Controller.TrainSeen());
                    return null;
                });

        LoggingTestKit.info(name + " is in State " + Controller.ControllerState.CLOSE)
                .expect(testKit.system(), () -> {
                    controller.tell(new Controller.TrainNotSeen());
                    return null;
                });
        LoggingTestKit.info(name + " is in State " + Controller.ControllerState.PRESENT)
                .expect(testKit.system(), () -> {
                    controller.tell(new Controller.TrainSeen());
                    return null;
                });
        LoggingTestKit.custom(msg ->  msg.message().contains(name + " is in State " + Controller.ControllerState.LEAVING) ||
                        msg.message().contains(name + "-Gate" + " is in State " + Gate.GateState.UP) ||
                        msg.message().contains(name + "-LightMachine" + " is in State " + LightMachine.LightMachineState.OFF))
                .withOccurrences(3)
                .expect(testKit.system(), () -> {
                    controller.tell(new Controller.TrainNotSeen());
                    return null;
                });
        LoggingTestKit.info(name + " is in State " + Controller.ControllerState.LEFT)
                .expect(testKit.system(), () -> {
                    controller.tell(new Controller.TrainSeen());
                    return null;
                });
        LoggingTestKit.info(name + " is in State " + Controller.ControllerState.AWAY)
                .expect(testKit.system(), () -> {
                    controller.tell(new Controller.TrainNotSeen());
                    return null;
                });
    }

    @AfterEach
    public void resetTestKit(){
        testKit.shutdownTestKit();
    }
}
