package actors.setup;

import akka.actor.typed.ActorRef;
import akka.actor.typed.javadsl.ActorContext;
import java.util.List;

/**
 * ComponentSetup: Provides logging function to log the state of actor-discovery and extracts the ActorRef
 */
public interface ComponentSetup {
  default <T, C> ActorRef<T> checkInstances(
    ActorContext<C> context,
    List<ActorRef<T>> list,
    Class<T> clazz
  ) {
    if (list.isEmpty()) {
      context.getLog().info("For class {} no instances found", clazz.toString());
      return null;
    }
    if (list.size() == 1) {
      context.getLog().info("For class {} exactly one instance found", clazz.toString());
      return list.getFirst();
    }
    context
      .getLog()
      .info(
        "For class {} multiple instances found: {}\n Returning first: {}",
        clazz.toString(),
        list,
        list.getFirst()
      );
    return list.getFirst();
  }
}
