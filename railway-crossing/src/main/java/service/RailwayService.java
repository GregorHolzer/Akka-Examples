package service;


import akka.actor.typed.javadsl.ActorContext;
import akka.discovery.ServiceDiscovery;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletionStage;

public class RailwayService {

    public static final String serviceName = "python-service-service.default.svc.cluster.local";

    public static final Integer port = 8000;

    private static final Duration timeout =  Duration.ofSeconds(10);

    private final ServiceDiscovery discovery;

    private ServiceDiscovery.ResolvedTarget address;


    public RailwayService(ServiceDiscovery discovery) {
        this.discovery = discovery;
    }

    public void discover(ActorContext<?> context) {
        while(true){
            try{
                ServiceDiscovery.Resolved resolved = discovery.lookup(serviceName, timeout).toCompletableFuture().get();
                if(resolved.getAddresses().isEmpty()){
                    context.getLog().error("Resolved Service contains no address, retrying...");
                }
                else {
                    address = resolved.getAddresses().getFirst();
                    context.getLog().info("Resolved Service contains address {} ", address.getAddress());
                    break;
                }
            }
            catch (Exception e){
                context.getLog().error("Failed to discover service {} after {}, message: {}, retrying..." , serviceName, timeout, e.getMessage());
            }
        }
    }

    private void sendRequest(ActorContext<?> context,String path, String crossingId){
        if(address.getAddress().isEmpty()){
            context.getLog().error("Resolved Service contains no address");
            return;
        }

        String url = "http:/" + address.getAddress().get() + ":" + port + path;

        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Cirrina-Sender-ID", crossingId)
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            context.getLog().info("Sent request to {}, Response code: {}, body: {}",
                    path, response.statusCode(), response.body());

        } catch (Exception e) {
            context.getLog().error("Failed to call {}: {}", path, e.getMessage());
        }
    }

    public void bellOn(ActorContext<?> context, String crossingId){
        sendRequest(context, "/bell/on", crossingId);
    }

    public void bellOff(ActorContext<?> context, String crossingId){
        sendRequest(context,"/bell/off", crossingId);
    }

    public void gateUp(ActorContext<?> context, String crossingId){sendRequest(context,"/gate/up", crossingId);}

    public void gateDown(ActorContext<?> context, String crossingId){sendRequest(context,"/gate/down", crossingId);}

    public void lightOn(ActorContext<?> context, String crossingId){sendRequest(context,"/light/on", crossingId);}

    public void lightOff(ActorContext<?> context, String crossingId){sendRequest(context,"/light/off", crossingId);}
}