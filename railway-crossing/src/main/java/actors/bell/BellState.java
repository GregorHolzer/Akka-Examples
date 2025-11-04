package actors.bell;

import actors.State;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class BellState implements State<BellState.State> {

  public enum State {
    OFF,
    ON
  }

  private final State state;

  @JsonCreator
  public BellState(@JsonProperty("state") State state) {
    this.state = state;
  }

  public BellState createWithState(State state) {
    return new BellState(state);
  }

  public State getState() {
    return state;
  }
}
