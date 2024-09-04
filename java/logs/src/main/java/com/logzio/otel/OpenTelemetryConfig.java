package com.logzio.otel;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.logs.GlobalLoggerProvider;
import io.opentelemetry.exporter.otlp.http.logs.OtlpHttpLogRecordExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.logs.export.BatchLogRecordProcessor;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.semconv.resource.attributes.ResourceAttributes;
import io.opentelemetry.api.common.Attributes;

public class OpenTelemetryConfig {

    private static final String DEFAULT_ENDPOINT = "<<LOGZIO_OTLP_LISTENER>>"; // e.g. https://otlp-listener.logz.io/v1/logs
    private static final String LOGZ_IO_TOKEN = "<LOGZIO_SHIPPING_TOKEN>";
    private static final String SERVICE_NAME = "java-otlp";

    public void initializeOpenTelemetry() {

        // set service name on all OTel signals
        Resource resource = Resource.getDefault().merge(Resource.create(
                Attributes.of(ResourceAttributes.SERVICE_NAME, SERVICE_NAME)));

        // Set up the OTLP log exporter with the endpoint and necessary headers
        OtlpHttpLogRecordExporter logExporter = OtlpHttpLogRecordExporter.builder()
                .setEndpoint(DEFAULT_ENDPOINT)
                .addHeader("Authorization", "Bearer " + LOGZ_IO_TOKEN)
                .build();

        // Initialize the logger provider
        SdkLoggerProvider sdkLoggerProvider = SdkLoggerProvider.builder()
                .setResource(resource)
                .addLogRecordProcessor(BatchLogRecordProcessor.builder(logExporter).build())
                .build();

        // create sdk object and set it as global
        OpenTelemetrySdk sdk = OpenTelemetrySdk.builder()
                        .setLoggerProvider(sdkLoggerProvider)
                        .build();
        GlobalOpenTelemetry.set(sdk);
        // connect logger
        GlobalLoggerProvider.set(sdk.getSdkLoggerProvider());
        // Add hook to close SDK, which flushes logs
        Runtime.getRuntime().addShutdownHook(new Thread(sdk::close));
    }
}
