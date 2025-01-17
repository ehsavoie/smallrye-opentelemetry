package io.smallrye.opentelemetry.implementation.cdi;

import static io.smallrye.opentelemetry.api.OpenTelemetryConfig.INSTRUMENTATION_NAME;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.inject.Disposes;
import jakarta.enterprise.inject.Produces;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdkBuilder;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.smallrye.opentelemetry.api.OpenTelemetryConfig;

@Singleton
public class OpenTelemetryProducer {
    @Inject
    OpenTelemetryConfig config;

    @Produces
    @Singleton
    public OpenTelemetry getOpenTelemetry() {
        AutoConfiguredOpenTelemetrySdkBuilder builder = AutoConfiguredOpenTelemetrySdk.builder();

        ClassLoader contextClassLoader = SecuritySupport.getContextClassLoader();
        if (contextClassLoader != null) {
            builder.setServiceClassLoader(contextClassLoader);
        }

        return builder
                .setResultAsGlobal(false)
                .registerShutdownHook(false)
                .addPropertiesSupplier(() -> config.properties())
                .build()
                .getOpenTelemetrySdk();
    }

    @Produces
    @Singleton
    public Tracer getTracer() {
        return CDI.current().select(OpenTelemetry.class).get().getTracer(INSTRUMENTATION_NAME);
    }

    @Produces
    @RequestScoped
    public Span getSpan() {
        return Span.current();
    }

    @Produces
    @RequestScoped
    public Baggage getBaggage() {
        return Baggage.current();
    }

    void close(@Disposes final OpenTelemetry openTelemetry) {
        OpenTelemetrySdk openTelemetrySdk = (OpenTelemetrySdk) openTelemetry;
        List<CompletableResultCode> shutdown = new ArrayList<>();
        shutdown.add(openTelemetrySdk.getSdkTracerProvider().shutdown());
        shutdown.add(openTelemetrySdk.getSdkMeterProvider().shutdown());
        shutdown.add(openTelemetrySdk.getSdkLoggerProvider().shutdown());
        CompletableResultCode.ofAll(shutdown).join(10, TimeUnit.SECONDS);
    }
}
