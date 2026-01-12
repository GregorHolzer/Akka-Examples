import actors.common.Command;
import actors.common.Guardian;
import akka.actor.typed.ActorSystem;
import akka.cluster.Cluster;
import akka.management.javadsl.AkkaManagement;

public class Main {

  /**
   * EntryPoint of the Application:
   * <lu>
   *   <li>Starts the {@link ActorSystem} with the {@link Guardian} Actor as User-Guardian.</li>
   *   <li>Starts the Akka-Cluster</li>
   * </lu>
   * @param args contains a path to the JSON configuration file
   */
  public static void main(String[] args) {
    if (args.length == 0 || args[0].isBlank()) {
      System.out.println("Please provide a configuration file");
      return;
    }
    //Start the Actor System with the Guardian Actor as User-Guardian
    ActorSystem<Command> system = ActorSystem.create(
      Guardian.create(args[0]),
      "railway-crossing"
    );
    //Start the Akka-Cluster
    Cluster.get(system);
    //Start Akka-Management
    AkkaManagement.get(system).start();
  }
}
