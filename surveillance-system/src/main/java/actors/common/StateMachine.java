package actors.common;

import akka.actor.typed.javadsl.ActorContext;

/**
 * Interface for finite state machine actors.
 * <p>
 * Provides default logging of the current state.
 * </p>
 */
public interface StateMachine<S extends Enum<S>> {
  /**
   * Logs the current state of the actor.
   *
   * @param context ActorContext for logging.
   * @param state The current State of the finite state machine.
   */
  default void logState(ActorContext<?> context, S state) {
    context.getLog().info("{} in state {}", context.getSelf().path().name(), state);
  }
}
