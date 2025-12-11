package actors.common;

import akka.actor.typed.javadsl.ActorContext;

public interface StateMachine<S extends Enum<S>> {
  default void logState(ActorContext<?> context, S state) {
    context.getLog().info("{} in state {}", context.getSelf().path().name(), state);
  }
}
