// tracing.js

'use strict';

const { NodeSDK } = require('@opentelemetry/sdk-node');
const { OTLPTraceExporter } = require('@opentelemetry/exporter-trace-otlp-grpc');
const { diag, DiagConsoleLogger, DiagLogLevel } = require('@opentelemetry/api');
const { Resource } = require('@opentelemetry/resources');

// Optional: Enable diagnostic logging (useful for debugging)
diag.setLogger(new DiagConsoleLogger(), DiagLogLevel.INFO);

function startTracing() {
  const exporter = new OTLPTraceExporter({
    url: process.env.OTEL_EXPORTER_OTLP_ENDPOINT || 'http://localhost:4317',
  });

  const sdk = new NodeSDK({
    traceExporter: exporter,
    resource: new Resource({
      'service.name': 'nodejs-app',
    }),
  });

  // Start the SDK with a try-catch to handle any initialization errors
  try {
    sdk.start();
    console.log('Tracing initialized');
  } catch (error) {
    console.log('Error initializing tracing', error);
  }

  // Optional: Gracefully shutdown tracing on process exit
  process.on('SIGTERM', () => {
    sdk.shutdown()
      .then(() => console.log('Tracing terminated'))
      .catch((error) => console.log('Error terminating tracing', error))
      .finally(() => process.exit(0));
  });
}

module.exports = { startTracing };
