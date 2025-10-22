package actors;

import akka.actor.typed.javadsl.ActorContext;
import akka.serialization.jackson.CborSerializable;

public interface State<S extends Enum<S>> extends CborSerializable {

    default void logState(S state, ActorContext<?> context){
        String actorName = context.getSelf().path().name();
        context.getLog().info("{} is in State: {}", actorName, state);
    }

    public S getState();
}
