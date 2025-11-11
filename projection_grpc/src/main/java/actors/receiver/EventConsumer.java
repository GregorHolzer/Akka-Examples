package actors.receiver;

import actors.sender.Sender;
import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.Behavior;
import akka.japi.Pair;
import akka.persistence.Persistence;
import akka.projection.ProjectionId;
import akka.projection.eventsourced.javadsl.EventSourcedProvider;
import akka.projection.grpc.consumer.javadsl.GrpcReadJournal;
import akka.projection.javadsl.SourceProvider;
import akka.projection.r2dbc.javadsl.R2dbcProjection;
import echo.Echo;
import akka.persistence.query.Offset;
import akka.persistence.query.typed.EventEnvelope;
import akka.projection.ProjectionBehavior;
import echo.EchoMessage;

import java.util.List;
import java.util.Optional;

public class EventConsumer {
    public static Behavior<ProjectionBehavior.Command> init(ActorSystem<?> system, ActorRef<Receiver.ReceiverCommand> receiver)  {
        int numberOfProjectionInstances = 1;
        String projectionName = "echo_projection";
        List<Pair<Integer, Integer>> sliceRanges = Persistence.get(system).getSliceRanges(numberOfProjectionInstances);

        GrpcReadJournal eventsBySlicesQuery = GrpcReadJournal.create(
                system,
                List.of(Echo.getDescriptor())
        );

        Pair<Integer, Integer> sliceRange = sliceRanges.getFirst();
        String projectionKey = eventsBySlicesQuery.streamId() + "-" + sliceRange.first() + "-" + sliceRange.second();
        ProjectionId projectionId = ProjectionId.of(projectionName, projectionKey);

        SourceProvider<Offset, EventEnvelope<EchoMessage>> sourceProvider = EventSourcedProvider.eventsBySlices(
                system,
                eventsBySlicesQuery,
                eventsBySlicesQuery.streamId(),
                sliceRange.first(),
                sliceRange.second()
        );

        return ProjectionBehavior.create(
                R2dbcProjection.atLeastOnceAsync(
                        projectionId,
                        Optional.empty(),
                        sourceProvider,
                        () -> new ReceiverEventHandler(receiver),
                        system));
    }
}