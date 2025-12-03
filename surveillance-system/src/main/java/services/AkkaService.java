package services;

import akka.actor.typed.javadsl.ActorContext;
import akka.http.javadsl.Http;
import akka.http.javadsl.model.HttpHeader;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;

import java.util.concurrent.CompletionStage;

public abstract class AkkaService {

  private final String ip;

  private final Integer port;

  public AkkaService(String ip, Integer port) {
    this.ip = ip;
    this.port = port;
  }

  CompletionStage<HttpResponse> sendRequest(ActorContext<?> context, HttpRequest request) {
    return Http.get(context.getSystem()).singleRequest(request);
  }

  HttpRequest buildPostRequest(String path) {
    String url = "http://localhost" + ":" + port + path;
    return HttpRequest.POST(url);
  }
}
