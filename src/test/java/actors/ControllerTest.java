package actors;

import actors.messages.Approaching;
import actors.messages.Leaving;
import akka.actor.testkit.typed.javadsl.ActorTestKit;
import akka.actor.testkit.typed.javadsl.LoggingTestKit;
import akka.actor.typed.ActorRef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;


public class ControllerTest {
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
        LoggingTestKit.info(name + " is in State " + Controller.ControllerState.APPROACHING)
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
        LoggingTestKit.info(name + " is in State " + Controller.ControllerState.LEAVING)
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
