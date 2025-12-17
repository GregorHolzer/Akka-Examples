package actors.common;

import akka.serialization.jackson.CborSerializable;

/** Command interface that is used for all messages */
public interface Command extends CborSerializable {}
