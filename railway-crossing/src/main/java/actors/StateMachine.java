package actors;


import akka.actor.typed.javadsl.ActorContext;

/// State Machine Interface to provide default Methods
public interface StateMachine<S extends Enum<S>> {

    default void logStateToConsole(S state, ActorContext<?> context) {
        String name = context.getSelf().path().name();
        context.getLog().info("{} is in State {}", name, state);
    }

}
