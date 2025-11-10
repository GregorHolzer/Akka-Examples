package actors.receiver;

import actors.sender.Sender;
import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorSystem;
import akka.persistence.cassandra.query.javadsl.CassandraReadJournal;
import akka.persistence.query.Offset;
import akka.persistence.r2dbc.query.javadsl.R2dbcReadJournal;
import akka.persistence.r2dbc.query.javadsl.R2dbcReadJournal$;
import akka.projection.javadsl.AtLeastOnceProjection;
import akka.projection.javadsl.SourceProvider;
import akka.projection.eventsourced.javadsl.EventSourcedProvider;
import akka.projection.eventsourced.EventEnvelope;
import akka.projection.ProjectionId;
import akka.projection.cassandra.javadsl.CassandraProjection;

public class EventConsumer {
    public static AtLeastOnceProjection<Offset, EventEnvelope<Sender.SenderEvent>> init(ActorSystem<?> system, ActorRef<Receiver.ReceiverCommand> receiver) {

        SourceProvider<Offset, EventEnvelope<Sender.SenderEvent>> sourceProvider =
                EventSourcedProvider.eventsByTag(
                        system,
                        R2dbcReadJournal.Identifier(),
                        "send_echo"
                );

        ProjectionId projectionId = ProjectionId.of("receiver-events", "send_echo");

        return CassandraProjection.atLeastOnce(
                projectionId,
                sourceProvider,
                () -> new ReceiverEventHandler(receiver)
        );

    }
}