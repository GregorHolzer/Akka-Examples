package services;

import actors.Surveillance;
import akka.actor.typed.javadsl.ActorContext;

//Dummy Services
public class SurveillanceServices {

  private Integer counter = 0;

  public void analyze(
    ActorContext<Surveillance.SurveillanceCommand> context,
    Surveillance.FoundPersons foundPersons
  ) {
      counter++;
    context.getLog().info("analyze(), hasThreat: {}", counter % 2 == 0);
    context.getSelf().tell(new Surveillance.Analyzed(foundPersons.image, counter % 2 == 0));
  }
}
