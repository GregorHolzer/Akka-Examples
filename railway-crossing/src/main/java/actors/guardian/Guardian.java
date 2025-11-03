package actors.guardian;

import actors.Command;
import actors.ComponentType;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import akka.discovery.Discovery;
import akka.discovery.ServiceDiscovery;
import service.RailwayService;

import java.time.Duration;
import java.util.concurrent.CompletionStage;

public class Guardian extends AbstractBehavior<Command> {

    private final RailwayService railwayService;

    public static Behavior<Command> create() {
        return Behaviors.setup(Guardian::new);
    }

    public Guardian(ActorContext<Command> context) {
        super(context);
        ServiceDiscovery discovery = Discovery.get(context.getSystem()).discovery();
        railwayService = new RailwayService(discovery);
        railwayService.discover(context);
        setupComponent();
    }

    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder().build();
    }

    private void setupComponent(){
        String crossingId = System.getenv("CROSSING_ID");
        String component_env =  System.getenv("COMPONENT_TYPE");
        if(crossingId==null){
            getContext().getLog().error("Service is not defined");
            return;
        }
        if (component_env==null){
            getContext().getLog().error("Component is not defined");
            return;
        }
        try{
            ComponentType componentType = ComponentType.valueOf(component_env);
            switch (componentType){
                case Controller -> {
                    getContext().spawn(ControllerSetup.create(crossingId), "ControllerSetup");
                    getContext().getLog().info("ControllerSetup has been started successfully");
                }
                case LightMachine -> {
                    getContext().spawn(LightMachineSetup.create(crossingId, railwayService), "LightMachineSetup");
                    getContext().getLog().info("LightMachineSetup has been started successfully");
                }
                case Gate -> {
                    getContext().spawn(GateSetup.create(crossingId, railwayService), "GateSetup");
                    getContext().getLog().info("GateSetup has been started successfully");
                }
                case Bell -> {
                    getContext().spawn(BellSetup.create(crossingId, railwayService), "BellSetup");
                    getContext().getLog().info("BellSetup has been started successfully");
                }
                default -> getContext().getLog().info("No Rule defined for Component_Type {}",  componentType);
            }
        }
        catch (Exception e){
            getContext().getLog().error("Error parsing EnvVariable: {}", e.getMessage());
            getContext().getSystem().terminate();
        }
    }
}