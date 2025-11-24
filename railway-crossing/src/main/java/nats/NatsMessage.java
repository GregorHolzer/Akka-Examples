package nats;

import exchange.ContextVariableProtos;

import java.util.List;
import java.util.Objects;

public class NatsMessage {
  public Boolean sensorValue = null;
  public Double trainSpeed = null;
  public String traceId = null;
  public String spanId = null;

  public NatsMessage(){}

  public boolean isValid() {
      return sensorValue != null && trainSpeed != null && traceId != null && spanId != null;
  }

    public static NatsMessage getNatsMessage(List<ContextVariableProtos.ContextVariable> dataList){
        NatsMessage natsMessage = new NatsMessage();
        for (ContextVariableProtos.ContextVariable contextVariable : dataList) {
            if (contextVariable.getName().equals("value")) {
                natsMessage.sensorValue = contextVariable.getValue().getBool();
            } else if (contextVariable.getName().equals("trainSpeed")) {
                natsMessage.trainSpeed = contextVariable.getValue().getDouble();
            } else if (contextVariable.getName().equals("traceId")) {
                natsMessage.traceId = contextVariable.getValue().getString();
            } else if (contextVariable.getName().equals("spanId")) {
                natsMessage.spanId = contextVariable.getValue().getString();
            }
        }
        return natsMessage;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        NatsMessage that = (NatsMessage) o;

        return (Objects.equals(sensorValue, that.sensorValue)) &&
                        (Objects.equals(trainSpeed, that.trainSpeed));
    }


    @Override
    public String toString() {
      return String.format("SensorValue: %s\nTrainSpeed: %s\nTraceId: %s\nSpanId: %s\n", sensorValue, trainSpeed, traceId, spanId);
    }
}
