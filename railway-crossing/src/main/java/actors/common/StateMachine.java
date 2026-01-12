package actors.common;

import akka.actor.typed.javadsl.ActorContext;

/**
 * Interface that provides a default function to log the state of a Finite State Machine
 * @param <S> Class provides states with an enum
 */
public interface StateMachine<S extends Enum<S>> {
  default void logState(ActorContext<?> context, S state) {
    context
      .getLog()
      .info("{} in state {}", context.getSelf().path().name(), state);
  }
}
