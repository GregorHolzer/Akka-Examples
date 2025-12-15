package actors.common;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.semconv.ServiceAttributes;
import java.time.Duration;

/**
 * Provides OpenTelemetry to measure detailed execution times
 */
public class Telemetry {

  private static OpenTelemetry openTelemetry = null;

  public static void initOpenTelemetry() {
    Configuration.NodeConfiguration config = Configuration.getNodeConfiguration();
    if(config != null && openTelemetry == null) {
      Resource resource = Resource.getDefault().merge(
              Resource.builder().put(ServiceAttributes.SERVICE_NAME, "akka-actors").build()
      );

      OtlpGrpcSpanExporter exporter = OtlpGrpcSpanExporter.builder()
              .setEndpoint("http://" + config.export_server_addr() + ":" + config.export_server_port())
              .setTimeout(Duration.ofSeconds(10))
              .build();

      SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
              .setResource(resource)
              .addSpanProcessor(SimpleSpanProcessor.builder(exporter).build())
              .build();

      openTelemetry = OpenTelemetrySdk.builder()
              .setTracerProvider(tracerProvider)
              .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
              .buildAndRegisterGlobal();
    }
  }

  public static OpenTelemetry getOpenTelemetry() {
    return openTelemetry;
  }

  public static Span createNewSpan(
    String prevTraceId,
    String prevSpanId,
    String scopeName,
    String spanBuilder
  ) {
    SpanContext parentSpanContext = SpanContext.createFromRemoteParent(
      prevTraceId,
      prevSpanId,
      TraceFlags.getSampled(),
      TraceState.getDefault()
    );

    Context parentContext = Context.root().with(Span.wrap(parentSpanContext));
    return openTelemetry
      .getTracer(scopeName)
      .spanBuilder(spanBuilder)
      .setParent(parentContext)
      .startSpan();
  }
}
