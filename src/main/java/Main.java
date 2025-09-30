import actors.Controller;
import actors.messages.SensorMessage;
import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorSystem;
import akka.cluster.Cluster;
import akka.cluster.sharding.typed.ShardingEnvelope;
import akka.cluster.sharding.typed.javadsl.ClusterSharding;
import akka.cluster.sharding.typed.javadsl.Entity;
import akka.cluster.sharding.typed.javadsl.EntityTypeKey;
import akka.http.javadsl.Http;
import akka.management.javadsl.AkkaManagement;
import api.RailWayCrossingAPI;


public class Main {

    private static ActorSystem<SensorMessage> system;
    private static ActorRef<ShardingEnvelope<SensorMessage>> controller;

    public static void main(String[] args) {
        system = ActorSystem.create(Controller.create(), "RailWayCrossing");
        Cluster cluster = Cluster.get(system);
        AkkaManagement.get(system).start();

            

        ClusterSharding sharding = ClusterSharding.get(system);

        EntityTypeKey<SensorMessage> controllerKey = EntityTypeKey.create(SensorMessage.class, "Controller");
        controller = sharding.init(Entity
                .of(controllerKey, context -> Controller.create()));

        setupAPI();
    }

    private static void setupAPI() {
        RailWayCrossingAPI api = new RailWayCrossingAPI(controller);

        int httpPort = system.settings()
                .config()
                .getInt("akka.http.server.default-http-port");

        Http.get(system)
                .newServerAt("localhost", httpPort)
                .bind(api.createRoutes());
    }
}
