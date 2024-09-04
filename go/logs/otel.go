package main

import (
	"context"
	"fmt"
	"log"

	"go.opentelemetry.io/otel/exporters/otlp/otlplog/otlploghttp"
	"go.opentelemetry.io/otel/exporters/stdout/stdoutlog"
	"go.opentelemetry.io/otel/log/global"
	sdklog "go.opentelemetry.io/otel/sdk/log"
)

func newLoggerProvider() (*sdklog.LoggerProvider, error) {
	// Create stdout log exporter
	stdoutExporter, err := stdoutlog.New(stdoutlog.WithPrettyPrint())
	if err != nil {
		return nil, fmt.Errorf("failed to create stdout exporter: %w", err)
	}

	// Create OTLP HTTP log exporter for Logz.io
	httpExporter, err := otlploghttp.New(context.Background(),
		otlploghttp.WithEndpoint("LOGZIO_OTLP_LISTENER"), // e.g. otlp-listener.logz.io
		otlploghttp.WithHeaders(map[string]string{
			"Authorization": "Bearer <LOG-SHIPPING-TOKEN>",
			"user-agent":    "logzio-go-logs-otlp",
		}),
		otlploghttp.WithURLPath("/v1/logs"),
	)
	if err != nil {
		return nil, fmt.Errorf("failed to create OTLP HTTP exporter: %w", err)
	}

	// Create a logger provider with both exporters
	loggerProvider := sdklog.NewLoggerProvider(
		sdklog.WithProcessor(sdklog.NewBatchProcessor(stdoutExporter)), // For stdout
		sdklog.WithProcessor(sdklog.NewBatchProcessor(httpExporter)),   // For HTTP export
	)

	return loggerProvider, nil
}

// setupOTelSDK bootstraps the OpenTelemetry logging pipeline.
func setupOTelSDK(ctx context.Context) (shutdown func(context.Context) error, err error) {
	// Set up logger provider.
	loggerProvider, err := newLoggerProvider()
	if err != nil {
		return nil, err
	}

	// Set the global logger provider
	global.SetLoggerProvider(loggerProvider)

	// Return a shutdown function
	shutdown = func(ctx context.Context) error {
		err := loggerProvider.Shutdown(ctx)
		if err != nil {
			log.Printf("Error during logger provider shutdown: %v", err)
		}
		return err
	}

	return shutdown, nil
}
