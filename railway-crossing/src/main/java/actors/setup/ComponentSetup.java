package actors.setup;

import akka.actor.typed.ActorRef;
import akka.actor.typed.javadsl.ActorContext;
import java.util.List;

/**
 * ComponentSetup provides utility functionality for logging the state of actor discovery
 * and for extracting {@link ActorRef} instances from a list.
 */
public interface ComponentSetup {
  /**
   * Extracts an {@link ActorRef} from a list of actor references, if possible.
   * <p>
   * The method behaves as follows:
   * <ul>
   *   <li>If the list is empty, logs a message and returns {@code null}.</li>
   *   <li>If the list contains exactly one element, logs a message and returns that element.</li>
   *   <li>If the list contains multiple elements, logs the list and returns the first element.</li>
   * </ul>
   *
   * @param context the {@link ActorContext} of the calling actor, used for logging
   * @param list    the list of {@link ActorRef} instances to choose from
   * @param clazz   the class of the actor instances (used for logging)
   * @param <T>     the type of messages the {@link ActorRef} can handle
   * @param <C>     the type of the calling actor
   * @return the first {@link ActorRef} from the list if available, or {@code null} if the list is empty
   */
  default <T, C> ActorRef<T> checkInstances(
    ActorContext<C> context,
    List<ActorRef<T>> list,
    Class<T> clazz
  ) {
    if (list.isEmpty()) {
      context
        .getLog()
        .warn("For class {} no instances found", clazz.toString());
      return null;
    }
    if (list.size() == 1) {
      context
        .getLog()
        .info("For class {} exactly one instance found", clazz.toString());
      return list.getFirst();
    }
    context
      .getLog()
      .info(
        "For class {} multiple instances found: {}\nReturning first: {}",
        clazz.toString(),
        list,
        list.getFirst()
      );
    return list.getFirst();
  }
}
