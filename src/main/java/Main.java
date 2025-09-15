import akka.actor.ActorSystem;
import akka.cluster.Cluster;
import akka.management.javadsl.AkkaManagement;

public class Main {
    public static void main(String[] args) {
        ActorSystem system = ActorSystem.create("RailWayCrossing");
        Cluster cluster = Cluster.get(system);
        AkkaManagement.get(system).start();
    }
}
