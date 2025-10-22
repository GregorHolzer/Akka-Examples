package actors;

import akka.serialization.jackson.CborSerializable;

public interface Command extends CborSerializable {}
