package actors.guardian;

import actors.Command;
import actors.ComponentType;
import actors.controller.Controller;
import actors.light_machine.LightMachine;

import actors.light_machine.commands.LightMachineCommand;
import actors.projection.*;
import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.japi.function.Function;
import akka.persistence.typed.PersistenceId;

import java.util.concurrent.CompletionStage;

public class GuardianActor extends AbstractBehavior<Command> {

    private static final String component_env = "Component_Type";

    private static final String entityId = "PersistenceId";


    public static Behavior<Command> create() {
        return Behaviors.setup(GuardianActor::new);
    }

    private GuardianActor(ActorContext<Command> context) {
        super(context);
    }

    @Override
    public Receive<Command> createReceive() {
        try{
            ComponentType componentType = ComponentType.valueOf(System.getenv(component_env));
            String id = System.getenv(entityId);
            switch (componentType){
                case Controller -> {
                    getContext().spawn(Controller.create(PersistenceId.of("Controller", id)), String.format("Controller")); //TODO: add PersistenceID
                    String host = "";
                    int port = 8000;

                }
                case LightMachine -> {
                    ActorRef<LightMachineCommand> actorRef = getContext().spawn(LightMachine.create(PersistenceId.of("LightMachine", "1")), String.format("LightMachine"));
                }
                default -> getContext().getLog().info("No Class defined for Component_Type {}",  componentType);
            }
        }
        catch (IllegalArgumentException e){
            getContext().getLog().error("Error parsing EnvVariable: {}", e.getMessage());
            getContext().getSystem().terminate();
        }
        return newReceiveBuilder().build();
    }
}
