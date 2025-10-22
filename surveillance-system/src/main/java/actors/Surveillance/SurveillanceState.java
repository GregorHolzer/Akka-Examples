package actors.Surveillance;

import actors.State;

public class SurveillanceState implements State<SurveillanceState.State> {

    public enum State {
        Analyzing,
        Alarm
    }

    private final State state;

    public SurveillanceState(State state) {
        this.state = state;
    }

    @Override
    public State getState() {
        return state;
    }
}
