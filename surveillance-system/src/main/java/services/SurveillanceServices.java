package services;

import actors.Detector.Detector;
import actors.Surveillance.Surveillance;
import akka.actor.typed.javadsl.ActorContext;

//Dummy Services
public class SurveillanceServices {

  private Integer counter = 0;

  public void analyze(
    ActorContext<Surveillance.SurveillanceCommand> context,
    Surveillance.ImageWrapper wrapper
  ) {
    context.getLog().info("SurveillanceServices.analyze()");
    wrapper.hasThread = (counter % 2 == 0);
    context.getSelf().tell(new Surveillance.Analyzed(wrapper));
  }
}
