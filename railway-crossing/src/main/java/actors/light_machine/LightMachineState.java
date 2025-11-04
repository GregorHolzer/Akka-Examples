package actors.light_machine;

import actors.State;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class LightMachineState implements State<LightMachineState.State> {

  public enum State {
    OFF,
    ON
  }

  private final State state;

  @JsonCreator
  public LightMachineState(@JsonProperty("state") State state) {
    this.state = state;
  }

  public LightMachineState createWithState(State state) {
    return new LightMachineState(state);
  }

  public State getState() {
    return state;
  }
}
