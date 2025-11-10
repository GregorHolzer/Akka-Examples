package actors.sender;

import akka.actor.typed.ActorSystem;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.japi.function.Function;
import akka.persistence.query.typed.EventEnvelope;
import akka.projection.grpc.producer.EventProducerSettings;
import akka.projection.grpc.producer.javadsl.EventProducer;
import akka.projection.grpc.producer.javadsl.EventProducerSource;
import akka.projection.grpc.producer.javadsl.Transformation;
import echo.EchoMessage;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

public class EventPublisher {

    public static Function<HttpRequest, CompletionStage<HttpResponse>> eventProducerService(ActorSystem<?> system) {
        Transformation transformation =
                Transformation.empty()
                        .registerEnvelopeMapper(
                                Sender.SendEchoEvent.class,
                                envelope -> Optional.of(transformSendEchoEvent(envelope))
                        );
        EventProducerSource eventProducerSource = new EventProducerSource(
                "Sender",
                "send_echo",
                transformation,
                EventProducerSettings.create(system)
        );
        return EventProducer.grpcServiceHandler(system, eventProducerSource);
    }

    private static EchoMessage transformSendEchoEvent(
            EventEnvelope<Sender.SendEchoEvent> envelope) {
        Sender.SendEchoEvent event = envelope.event();
        return EchoMessage.newBuilder()
                .setPayload(event.msg)
                .build();
    }
}
