import actors.Command;
import actors.controller.Controller;
import actors.guardian.GuardianActor;
import akka.actor.typed.ActorSystem;
import akka.cluster.Cluster;

public class Main {
    public static void main(String[] args) {
          ActorSystem<Command> system = ActorSystem.create(GuardianActor.create(), "RailWayCrossing");
    }
}
