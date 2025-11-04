package actors.controller;

import actors.State;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class ControllerState implements State<ControllerState.State> {

  public enum State {
    AWAY, //0
    APPROACHING,
    CLOSE,
    PRESENT,
    LEAVING,
    LEFT //5
  }

  private final State state;

  @JsonCreator
  public ControllerState(@JsonProperty("state") State state) {
    this.state = state;
  }

  public ControllerState createWithState(State state) {
    return new ControllerState(state);
  }

  public State getState() {
    return state;
  }
}
