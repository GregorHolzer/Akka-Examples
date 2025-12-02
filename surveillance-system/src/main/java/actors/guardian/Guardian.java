package actors.guardian;

import Configuration.Configuration;
import actors.Command;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;

public class Guardian extends AbstractBehavior<Command> {

  public static Behavior<Command> create(String configPath) {
    return Behaviors.setup(context -> new Guardian(context, configPath));
  }

  private Guardian(ActorContext<Command> context, String configPath) {
    super(context);
    if (
      Configuration.initConfig(context, configPath) == Configuration.ConfigurationStatus.Success
    ) {
      setupComponents();
    }
  }

  private void setupComponents() {}

  @Override
  public Receive<Command> createReceive() {
    return newReceiveBuilder().build();
  }
}
