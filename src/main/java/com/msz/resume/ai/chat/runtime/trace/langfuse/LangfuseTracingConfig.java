package com.msz.resume.ai.chat.runtime.trace.langfuse;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

@Slf4j
@Configuration
@EnableConfigurationProperties(LangfuseTraceProperties.class)
public class LangfuseTracingConfig {

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(name = "jarvis.trace.langfuse.enabled", havingValue = "true")
    public SdkTracerProvider langfuseTracerProvider(LangfuseTraceProperties properties) {
        if (!properties.configured()) {
            log.warn("[Langfuse] tracing is enabled but credentials are incomplete; exporting is disabled");
            return SdkTracerProvider.builder().build();
        }

        String auth = Base64.getEncoder().encodeToString(
                (properties.getPublicKey() + ":" + properties.getSecretKey()).getBytes(StandardCharsets.UTF_8)
        );
        OtlpHttpSpanExporter exporter = OtlpHttpSpanExporter.builder()
                .setEndpoint(properties.tracesEndpoint())
                .addHeader("Authorization", "Basic " + auth)
                .addHeader("x-langfuse-ingestion-version", "4")
                .setTimeout(Duration.ofMillis(properties.getExportTimeoutMs()))
                .build();

        Resource resource = Resource.getDefault().merge(Resource.builder()
                .put("service.name", properties.getServiceName())
                .put("deployment.environment", properties.getEnvironment())
                .build());

        log.info("[Langfuse] OTLP exporter configured: endpoint={}, environment={}",
                properties.tracesEndpoint(), properties.getEnvironment());
        return SdkTracerProvider.builder()
                .setResource(resource)
                .addSpanProcessor(BatchSpanProcessor.builder(exporter).build())
                .build();
    }

    @Bean
    @ConditionalOnProperty(name = "jarvis.trace.langfuse.enabled", havingValue = "true")
    public OpenTelemetry langfuseOpenTelemetry(SdkTracerProvider langfuseTracerProvider) {
        return OpenTelemetrySdk.builder()
                .setTracerProvider(langfuseTracerProvider)
                .build();
    }

    @Bean
    @ConditionalOnProperty(name = "jarvis.trace.langfuse.enabled", havingValue = "true")
    public Tracer langfuseTracer(OpenTelemetry langfuseOpenTelemetry) {
        return langfuseOpenTelemetry.getTracer("jarvis-langfuse");
    }
}
