import actors.Command;
import actors.guardian.Guardian;
import akka.actor.typed.ActorSystem;
import akka.cluster.Cluster;
import akka.management.cluster.bootstrap.ClusterBootstrap;
import akka.management.javadsl.AkkaManagement;


public class Main {

  public static void main(String[] args) {
    if(args.length == 0 || args[0].isBlank()){
        System.out.println("Please provide a configuration file");
        return;
    }
    ActorSystem<Command> system = ActorSystem.create(Guardian.create(args[0]), "railway-crossing");
    Cluster.get(system);
    AkkaManagement.get(system).start();
    ClusterBootstrap.get(system).start();
  }
}
