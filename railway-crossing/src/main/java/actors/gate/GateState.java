package actors.gate;

import actors.State;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class GateState implements State<GateState.State> {

  public enum State {
    OPEN,
    CLOSED
  }

  private final State state;

  @JsonCreator
  public GateState(@JsonProperty("state") State state) {
    this.state = state;
  }

  @Override
  public GateState createWithState(State state) {
    return new GateState(state);
  }

  @Override
  public State getState() {
    return state;
  }
}
