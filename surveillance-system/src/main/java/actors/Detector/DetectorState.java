package actors.Detector;

import actors.State;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;


public class DetectorState implements State<DetectorState.State> {
    public enum State {
        Capturing,
        Processing,
        Alarm
    }

    private final State state;

    @JsonCreator
    public DetectorState(@JsonProperty("state") State state) {
        this.state = state;
        invokeService(state);
    }

    public  State getState() {
        return state;
    }

    private void invokeService(State s){
        switch (s){
            case Capturing: {}  //Todo: invoke capturePerson
            case Processing: {} //Todo: invoke detectPerson
            case Alarm: {}      //Todo: invoke alarmOn/Off
        }
    }
}
