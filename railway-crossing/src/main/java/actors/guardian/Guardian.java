package actors.guardian;

import actors.Command;
import actors.ComponentType;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;

public class Guardian extends AbstractBehavior<Command> {

    public static Behavior<Command> create() {
        return Behaviors.setup(Guardian::new);
    }

    public Guardian(ActorContext<Command> context) {
        super(context);
        setupComponent();
    }

    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder().build();
    }

    private void setupComponent(){
        String serviceName = System.getenv("SERVICE_ID");
        String component_env =  System.getenv("COMPONENT_TYPE");
        if(serviceName==null){
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
                    getContext().spawn(ControllerSetup.create(serviceName), "ControllerSetup");
                    getContext().getLog().info("ControllerSetup has been started successfully");
                }
                case LightMachine -> {
                    getContext().spawn(LightMachineSetup.create(serviceName), "LightMachineSetup");
                    getContext().getLog().info("LightMachineSetup has been started successfully");
                }
                case Gate -> {
                    getContext().spawn(GateSetup.create(serviceName), "GateSetup");
                    getContext().getLog().info("GateSetup has been started successfully");
                }
                case Bell -> {
                    getContext().spawn(BellSetup.create(serviceName), "BellSetup");
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