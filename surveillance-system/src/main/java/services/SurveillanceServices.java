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
    context.getLog().info("SurveillanceServices.analyze()");
    counter++;
    context.getSelf().tell(new Surveillance.Analyzed(foundPersons.image, counter % 2 == 0));
  }
}
