package actors.guardian;


import actors.Command;
import actors.ComponentType;
import actors.bell.Bell;
import actors.gate.Gate;
import actors.light_machine.LightMachine;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import akka.actor.typed.receptionist.Receptionist;
import akka.actor.typed.receptionist.ServiceKey;
import akka.persistence.typed.PersistenceId;

public class Guardian extends AbstractBehavior<Command> {

    private final String serviceName = System.getenv("SERVICE_NAME");

    private final String component_env =  System.getenv("COMPONENT_TYPE");

    public static Behavior<Command> create() {
        return Behaviors.setup(Guardian::new);
    }

    public Guardian(ActorContext<Command> context) {
        super(context);

    }

    @Override
    public Receive<Command> createReceive() {
        setupComponent();
        return newReceiveBuilder().build();
    }

    private void setupComponent(){
        try{
            ComponentType componentType = ComponentType.valueOf(System.getenv(component_env));
            switch (componentType){
                case Controller -> {
                    getContext().spawn(ControllerSetup.create(serviceName), "ControllerSetup");
                }
                case LightMachine -> {
                }
                case Gate -> {

                }
                case Bell -> {
                    getContext().spawn(BellSetup.create(serviceName), "BellSetup");
                }
                default -> getContext().getLog().info("No Rule defined for Component_Type {}",  componentType);
            }
        }
        catch (IllegalArgumentException e){
            getContext().getLog().error("Error parsing EnvVariable: {}", e.getMessage());
            getContext().getSystem().terminate();
        }
    }
}