package services;

import akka.actor.typed.ActorSystem;
import akka.actor.typed.javadsl.ActorContext;
import akka.http.javadsl.Http;
import akka.http.javadsl.model.*;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import exchange.ContextVariableProtos;

import java.util.HashMap;
import java.util.concurrent.CompletionStage;

public interface AkkaService {

  default CompletionStage<HttpResponse> sendRequest(ActorContext<?> context, HttpRequest request) {
    return Http.get(context.getSystem()).singleRequest(request);
  }

  default HttpRequest buildPostRequest(String host, Integer port, String path) {
    return HttpRequest.POST(getUrl(host, port, path));
  }

  default HttpRequest buildPostRequest(String host, Integer port, String path, byte[] body) {
    return HttpRequest.POST(getUrl(host, port, path))
            .withEntity(
                    HttpEntities.create(ContentTypes.APPLICATION_OCTET_STREAM, akka.util.ByteString.fromArray(body))
            );
  }

  default byte[] buildProtoRequestBody(HashMap<String, Object> values) throws IllegalArgumentException {
    ContextVariableProtos.ContextVariables.Builder var = ContextVariableProtos.ContextVariables.newBuilder();
    values.forEach((name,value)->{
      ContextVariableProtos.ContextVariable.Builder contextVariable = ContextVariableProtos.ContextVariable.newBuilder();
      contextVariable.setName(name);
      switch (value) {
        case Integer i ->
                contextVariable.setValue(ContextVariableProtos.Value.newBuilder().setInteger(i).build());
        case byte[] bytes ->
                contextVariable.setValue(ContextVariableProtos.Value.newBuilder().setBytes(ByteString.copyFrom(bytes)).build());
        case String s ->
                contextVariable.setValue(ContextVariableProtos.Value.newBuilder().setString(s).build());
        default -> throw new IllegalArgumentException("Invalid value type");
      }
      var.addData(contextVariable.build());
    });
    return var.build().toByteArray();
  }

  default CompletionStage<ContextVariableProtos.ContextVariables> extractContextVariable(ActorSystem<?> system, HttpResponse response) {
    return response.entity().getDataBytes().runFold(akka.util.ByteString.emptyByteString(), akka.util.ByteString::concat, system)
            .thenApply(bytes -> {
              try {
                return ContextVariableProtos.ContextVariables.parseFrom(bytes.toArray());
              } catch (InvalidProtocolBufferException e) {
                return ContextVariableProtos.ContextVariables.newBuilder().build();
              }
            });
  }

  default String getUrl(String host, Integer port, String path){
    return  "http://" + host + ":" + port + path;
  }
}
