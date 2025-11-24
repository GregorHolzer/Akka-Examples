package open_telemetry;

import actors.NodeConfig;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.semconv.ServiceAttributes;

import java.time.Duration;

public class TelemetryJaeger {

    public static OpenTelemetry openTelemetry;

    public static io.opentelemetry.api.OpenTelemetry initOpenTelemetry(NodeConfig config) {
        Resource resource = Resource.getDefault()
                .merge(Resource.builder()
                        .put(ServiceAttributes.SERVICE_NAME, "akka-actors")
                        .build());

        OtlpGrpcSpanExporter exporter = OtlpGrpcSpanExporter.builder()
                .setEndpoint("http://" + config.jaeger_server_addr() + ":" + config.jaeger_server_port())
                .setTimeout(Duration.ofSeconds(10))
                .build();

        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .setResource(resource)
                .addSpanProcessor(BatchSpanProcessor.builder(exporter).build())
                .build();


        openTelemetry = OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .setPropagators(
                        ContextPropagators.create(W3CTraceContextPropagator.getInstance())
                )
                .buildAndRegisterGlobal();
        return openTelemetry;
    }
}
