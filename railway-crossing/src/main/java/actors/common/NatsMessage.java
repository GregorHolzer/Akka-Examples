package actors.common;

import exchange.ContextVariableProtos;
import java.util.List;
import java.util.Objects;

/**
 * Represents a received Message from NATS: Stores
 */
public record NatsMessage(Boolean sensorValue, Double trainSpeed, String traceId, String spanId) {

  /** Checks if all fields are initialized */
  public boolean isValid() {
    return sensorValue != null && trainSpeed != null && traceId != null && spanId != null;
  }

  /** Creates a new NatsMessage from the DataList of a ContextVariable */
  public static NatsMessage getNatsMessage(List<ContextVariableProtos.ContextVariable> dataList) {
    Boolean sensorValue = null;
    Double trainSpeed = null;
    String traceId = null;
    String spanId = null;
    for (ContextVariableProtos.ContextVariable contextVariable : dataList) {
      if (contextVariable.getName().equals("value")) {
        sensorValue = contextVariable.getValue().getBool();
      } else if (contextVariable.getName().equals("trainSpeed")) {
        trainSpeed = contextVariable.getValue().getDouble();
      } else if (contextVariable.getName().equals("traceId")) {
        traceId = contextVariable.getValue().getString();
      } else if (contextVariable.getName().equals("spanId")) {
        spanId = contextVariable.getValue().getString();
      }
    }
    return new NatsMessage(sensorValue, trainSpeed, traceId, spanId);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    NatsMessage that = (NatsMessage) o;

    return (
      (Objects.equals(sensorValue, that.sensorValue)) &&
      (Objects.equals(trainSpeed, that.trainSpeed))
    );
  }
}
