package actors.services;

import akka.actor.typed.ActorSystem;
import akka.actor.typed.javadsl.ActorContext;
import akka.http.javadsl.Http;
import akka.http.javadsl.model.*;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import exchange.ContextVariableProtos;

import java.util.HashMap;
import java.util.concurrent.CompletionStage;

/**
 * Provides common functions to be used by service-invocations
 */
public interface AkkaService {
  /**
   * Sends an HTTP request.
   *
   * @param context the actor context to access the actor system
   * @param request the HTTP request to send
   * @return a future of the HTTP response
   */
  default CompletionStage<HttpResponse> sendRequest(
    ActorContext<?> context,
    HttpRequest request
  ) {
    return Http.get(context.getSystem()).singleRequest(request);
  }

  /**
   * Builds a simple HTTP POST request without a body.
   *
   * @param host the target host
   * @param port the target port
   * @param path the request path
   * @return a configured POST request
   */
  default HttpRequest buildPostRequest(String host, Integer port, String path) {
    return HttpRequest.POST(getUrl(host, port, path));
  }

  /**
   * Builds an HTTP POST request with a request body.
   *
   * @param host the target host
   * @param port the target port
   * @param path the request path
   * @param body the request body
   * @return a configured POST request with entity
   */
  default HttpRequest buildPostRequest(
    String host,
    Integer port,
    String path,
    HttpHeader header,
    byte[] body
  ) {
    return HttpRequest.POST(getUrl(host, port, path))
      .addHeader(header)
      .withEntity(
      HttpEntities.create(
        ContentTypes.APPLICATION_OCTET_STREAM,
        akka.util.ByteString.fromArray(body)
      )
    );
  }

  /**
   * Builds a Protobuf payload based on a map of values.
   * <p>
   * Supported value types: {@code Integer}, {@code byte[]}, and {@code String}.
   * </p>
   * @param values a map of variable names to values
   * @return the serialized Protobuf byte array
   * @throws IllegalArgumentException for unsupported value types
   */
  default byte[] buildProtoRequestBody(HashMap<String, Object> values)
    throws IllegalArgumentException {
    ContextVariableProtos.ContextVariables.Builder var =
      ContextVariableProtos.ContextVariables.newBuilder();
    values.forEach((name, value) -> {
      ContextVariableProtos.ContextVariable.Builder contextVariable =
        ContextVariableProtos.ContextVariable.newBuilder();
      contextVariable.setName(name);
      switch (value) {
        case Integer i -> contextVariable.setValue(
          ContextVariableProtos.Value.newBuilder().setInteger(i).build()
        );
        case Double d -> contextVariable.setValue(
                ContextVariableProtos.Value.newBuilder().setDouble(d).build()
        );
        case byte[] bytes -> contextVariable.setValue(
          ContextVariableProtos.Value.newBuilder()
            .setBytes(ByteString.copyFrom(bytes))
            .build()
        );
        case String s -> contextVariable.setValue(
          ContextVariableProtos.Value.newBuilder().setString(s).build()
        );
        default -> throw new IllegalArgumentException(
          "Invalid value type: " + value.getClass()
        );
      }
      var.addData(contextVariable.build());
    });
    return var.build().toByteArray();
  }

  /**
   * Extracts a Protobuf {@code ContextVariables} object from an HTTP response.
   *
   * @param system the actor system to run the stream
   * @param response the HTTP response containing the Protobuf body
   * @return a future of parsed {@code ContextVariables}
   */
  default CompletionStage<
    ContextVariableProtos.ContextVariables
  > extractContextVariable(ActorSystem<?> system, HttpResponse response) {
    return response
      .entity()
      .getDataBytes()
      .runFold(
        akka.util.ByteString.emptyByteString(),
        akka.util.ByteString::concat,
        system
      )
      .thenApply(bytes -> {
        try {
          return ContextVariableProtos.ContextVariables.parseFrom(
            bytes.toArray()
          );
        } catch (InvalidProtocolBufferException e) {
          return ContextVariableProtos.ContextVariables.newBuilder().build();
        }
      });
  }

  /**
   * Constructs a full HTTP URL from host, port, and path.
   *
   * @param host the host
   * @param port the port
   * @param path the path
   * @return the combined URL string
   */
  default String getUrl(String host, Integer port, String path) {
    return "http://" + host + ":" + port + path;
  }
}
