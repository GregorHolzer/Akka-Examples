package actors;

import akka.actor.typed.javadsl.ActorContext;
import akka.serialization.jackson.CborSerializable;

public interface State<S extends Enum<S>> extends CborSerializable {
  default void logState(S state, ActorContext<?> context) {
    String actorName = context.getSelf().path().name();
    context.getLog().info("{} is in State: {}", actorName, state);
  }

  public State<S> createWithState(S state);

  public S getState();

  default State<S> advanceState() {
    S currentState = getState();
    int currentStateOrdinal = currentState.ordinal();
    S[] allStates = currentState.getDeclaringClass().getEnumConstants();
    S nextState;
    if (currentStateOrdinal < allStates.length - 1) {
      nextState = allStates[currentStateOrdinal + 1];
    } else {
      nextState = allStates[0];
    }
    return createWithState(nextState);
  }
}
