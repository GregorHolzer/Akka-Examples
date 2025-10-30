import actors.Command;
import actors.guardian.Guardian;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.receptionist.Receptionist;
import akka.cluster.Cluster;
import akka.discovery.Discovery;
import akka.discovery.ServiceDiscovery;
import akka.management.cluster.bootstrap.ClusterBootstrap;
import akka.management.javadsl.AkkaManagement;

import java.time.Duration;
import java.util.concurrent.CompletionStage;

public class Main {
    public static void main(String[] args) {
        ActorSystem<Command> system = ActorSystem.create(Guardian.create(), "railway-crossing");
        Cluster cluster = Cluster.get(system);
        AkkaManagement.get(system).start();
        ClusterBootstrap.get(system).start();
        ServiceDiscovery discovery = Discovery.get(system).discovery();
        String serviceName = "python-service-service.default.svc.cluster.local";
        CompletionStage<ServiceDiscovery.Resolved> result =
                discovery.lookup(serviceName, Duration.ofSeconds(3));
        result.thenAccept(service -> {
            service.getAddresses().forEach(address -> {system.log().info("Found service at {}",address.toString());});
        });
    }
}