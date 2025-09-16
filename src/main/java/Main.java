import actors.Controller;
import akka.actor.typed.ActorSystem;
import akka.cluster.Cluster;
import akka.management.javadsl.AkkaManagement;

public class Main {
    public static void main(String[] args) {
        ActorSystem<Controller.SensorMessage> system = ActorSystem.create(Controller.create(), "RailWayCrossing");
        Cluster cluster = Cluster.get(system);
        AkkaManagement.get(system).start();


    }
}
