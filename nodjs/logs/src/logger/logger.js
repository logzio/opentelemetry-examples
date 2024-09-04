const { LoggerProvider, SimpleLogRecordProcessor } = require('@opentelemetry/sdk-logs');
const { OTLPLogExporter } = require('@opentelemetry/exporter-logs-otlp-proto');
const { Resource } = require('@opentelemetry/resources');

const resource = new Resource({'service.name': 'your-service-name'});
const loggerProvider = new LoggerProvider({ resource });

const otlpExporter = new OTLPLogExporter({
  url: '<<LOGZIO_OTLP_LISTENER>>', // e.g. https://otlp-listener.logz.io/v1/logs
  headers: {
    Authorization: 'Bearer <<LOG_SHIPPING_TOKEN>>', 
    'user-agent': 'logzio-nodejs-logs-otlp'
  }
});

loggerProvider.addLogRecordProcessor(new SimpleLogRecordProcessor(otlpExporter));

const logger = loggerProvider.getLogger('example_logger');
module.exports.logger = logger;
