package actor;

import actors.NodeConfig;
import actors.controller.Controller;
import actors.gate.Gate;
import actors.light_machine.LightMachine;
import akka.actor.testkit.typed.javadsl.ActorTestKit;
import akka.actor.testkit.typed.javadsl.LoggingTestKit;
import akka.actor.testkit.typed.javadsl.TestProbe;
import akka.actor.typed.ActorRef;
import open_telemetry.TelemetryJaeger;
import org.junit.AfterClass;
import org.junit.Test;

public class ControllerTest {

    private static final NodeConfig nodeConfig = new NodeConfig(null, null, null, null, 1, "localhost", 4317);

  private static final Double trainSpeed = 50.0;

  private static final String traceId = "trace1";

  private static final String spanId = "span1";

  private static final ActorTestKit testKit = ActorTestKit.create();

  private final TestProbe<Gate.GateCommand> gate = testKit.createTestProbe(Gate.GateCommand.class);

  private final TestProbe<LightMachine.LightMachineCommand> lightMachine = testKit.createTestProbe(
    LightMachine.LightMachineCommand.class
  );

  @Test
  public void initialState() {
      TelemetryJaeger.initOpenTelemetry(nodeConfig);
    ActorRef<Controller.ControllerCommand> controller = testKit.spawn(
      Controller.create(gate.getRef(), lightMachine.getRef()),
      "controller"
    );
    LoggingTestKit.info("controller in state Approaching").expect(testKit.system(), () -> {
      controller.tell(new Controller.CommandSensorSeen(trainSpeed, traceId, spanId));
      return null;
    });
    LoggingTestKit.info("controller in state Close").expect(testKit.system(), () -> {
      controller.tell(new Controller.CommandSensorNotSeen(trainSpeed, traceId, spanId));
      return null;
    });
    gate.expectMessageClass(Gate.CommandClose.class);
    lightMachine.expectMessageClass(LightMachine.CommandTurnOn.class);
    LoggingTestKit.info("controller in state Present").expect(testKit.system(), () -> {
      controller.tell(new Controller.CommandSensorSeen(trainSpeed, traceId, spanId));
      return null;
    });
    LoggingTestKit.info("controller in state Leaving").expect(testKit.system(), () -> {
      controller.tell(new Controller.CommandSensorNotSeen(trainSpeed, traceId, spanId));
      return null;
    });
    gate.expectMessageClass(Gate.CommandOpen.class);
    lightMachine.expectMessageClass(LightMachine.CommandTurnOff.class);
    LoggingTestKit.info("controller in state Left").expect(testKit.system(), () -> {
      controller.tell(new Controller.CommandSensorSeen(trainSpeed, traceId, spanId));
      return null;
    });
    LoggingTestKit.info("controller in state Away").expect(testKit.system(), () -> {
      controller.tell(new Controller.CommandSensorNotSeen(trainSpeed, traceId, spanId));
      return null;
    });
  }

  @Test
  public void duplicateCommands() {
    ActorRef<Controller.ControllerCommand> controller = testKit.spawn(
      Controller.create(gate.getRef(), lightMachine.getRef()),
      "controller1"
    );
    LoggingTestKit.info("controller1 in state ")
      .withOccurrences(0)
      .expect(testKit.system(), () -> {
        controller.tell(new Controller.CommandSensorNotSeen(trainSpeed, traceId, spanId));
        return null;
      });
    LoggingTestKit.info("controller1 in state Approaching").expect(testKit.system(), () -> {
      controller.tell(new Controller.CommandSensorSeen(trainSpeed, traceId, spanId));
      return null;
    });
    LoggingTestKit.info("controller1 in state ")
      .withOccurrences(0)
      .expect(testKit.system(), () -> {
        controller.tell(new Controller.CommandSensorSeen(trainSpeed, traceId, spanId));
        return null;
      });
    LoggingTestKit.info("controller1 in state Close").expect(testKit.system(), () -> {
      controller.tell(new Controller.CommandSensorNotSeen(trainSpeed, traceId, spanId));
      return null;
    });
    LoggingTestKit.info("controller1 in state ")
      .withOccurrences(0)
      .expect(testKit.system(), () -> {
        controller.tell(new Controller.CommandSensorNotSeen(trainSpeed, traceId, spanId));
        return null;
      });
    LoggingTestKit.info("controller1 in state Present").expect(testKit.system(), () -> {
      controller.tell(new Controller.CommandSensorSeen(trainSpeed, traceId, spanId));
      return null;
    });
    LoggingTestKit.info("controller1 in state ")
      .withOccurrences(0)
      .expect(testKit.system(), () -> {
        controller.tell(new Controller.CommandSensorSeen(trainSpeed, traceId, spanId));
        return null;
      });
    LoggingTestKit.info("controller1 in state Leaving").expect(testKit.system(), () -> {
      controller.tell(new Controller.CommandSensorNotSeen(trainSpeed, traceId, spanId));
      return null;
    });
    LoggingTestKit.info("controller1 in state ")
      .withOccurrences(0)
      .expect(testKit.system(), () -> {
        controller.tell(new Controller.CommandSensorNotSeen(trainSpeed, traceId, spanId));
        return null;
      });
    LoggingTestKit.info("controller1 in state Left").expect(testKit.system(), () -> {
      controller.tell(new Controller.CommandSensorSeen(trainSpeed, traceId, spanId));
      return null;
    });
    LoggingTestKit.info("controller1 in state ")
      .withOccurrences(0)
      .expect(testKit.system(), () -> {
        controller.tell(new Controller.CommandSensorSeen(trainSpeed, traceId, spanId));
        return null;
      });
  }

  @AfterClass
  public static void cleanUp() {
    testKit.shutdownTestKit();
  }
}
