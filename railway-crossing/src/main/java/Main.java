import actors.Command;
import actors.guardian.Guardian;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.receptionist.Receptionist;
import akka.cluster.Cluster;
import akka.management.cluster.bootstrap.ClusterBootstrap;
import akka.management.javadsl.AkkaManagement;

public class Main {
    public static void main(String[] args) {
        ActorSystem<Command> system = ActorSystem.create(Guardian.create(), "railway-crossing");
        Cluster cluster = Cluster.get(system);
        AkkaManagement.get(system).start();
        ClusterBootstrap.get(system).start();
    }
}