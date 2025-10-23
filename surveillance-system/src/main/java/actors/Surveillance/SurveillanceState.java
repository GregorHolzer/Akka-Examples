package actors.Surveillance;

import actors.State;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class SurveillanceState implements State<SurveillanceState.State> {

    public enum State {
        Analyzing,
        Alarm
    }

    private final State state;

    @JsonCreator
    public SurveillanceState(@JsonProperty("state") State state) {
        this.state = state;
    }

    @Override
    public State getState() {
        return state;
    }
}
