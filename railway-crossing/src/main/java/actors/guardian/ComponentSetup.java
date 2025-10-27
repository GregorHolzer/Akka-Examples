package actors.guardian;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.receptionist.Receptionist;

import java.util.List;

public interface ComponentSetup {

    default <T, C> ActorRef<T> checkInstances(ActorContext<C> context, List<ActorRef<T>> list, Class<T> clazz) {
        if(list.isEmpty()){
            context.getLog().info("For class {} no instances found", clazz.toString());
            return null;
        }
        if(list.size()==1){
            context.getLog().info("For class {} exactly one instance found", clazz.toString());
            return list.getFirst();
        }
        context.getLog().info("For class {} multiple instances found: {}\n Returning first: {}", clazz.toString(), list, list.getFirst());
        return list.getFirst();
    }

}
