import actors.guardian.Guardian;
import akka.actor.typed.ActorSystem;

public class Main {
    public static void main(String[] args) {
        ActorSystem.create(Guardian.create(), "grpc-projection");
    }
}
