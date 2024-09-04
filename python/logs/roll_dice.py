from flask import Flask
import random
import logging

from opentelemetry._logs import set_logger_provider
from opentelemetry.exporter.otlp.proto.http._log_exporter import OTLPLogExporter
from opentelemetry.sdk.resources import Resource
from opentelemetry.sdk._logs import LoggerProvider, LoggingHandler
from opentelemetry.sdk._logs.export import BatchLogRecordProcessor

# Configuration
service_name = "roll-dice"
logzio_endpoint = "<<LOGZIO_OTLP_LISTENER>>" # // e.g. https://otlp-listener.logz.io/v1/logs
logzio_token = "<LOGZIO_SHIPPING_TOKEN>"

# Set up OpenTelemetry resources
resource = Resource.create({"service.name": service_name})

# Set up Logger Provider and OTLP Log Exporter (HTTP/JSON)
logger_provider = LoggerProvider(resource=resource)
set_logger_provider(logger_provider)
log_exporter = OTLPLogExporter(
    endpoint=logzio_endpoint,
    headers={"Authorization": f"Bearer {logzio_token}"}
)
logger_provider.add_log_record_processor(BatchLogRecordProcessor(log_exporter))

# Set up a specific logger for the application
logger = logging.getLogger("app")
logger.setLevel(logging.INFO)

# Attach OTLP handler to the specific logger
otlp_handler = LoggingHandler(logger_provider=logger_provider)
logger.addHandler(otlp_handler)

# Attach a StreamHandler to log to the console
console_handler = logging.StreamHandler()
console_handler.setLevel(logging.INFO)
formatter = logging.Formatter('%(asctime)s - %(name)s - %(levelname)s - %(message)s')
console_handler.setFormatter(formatter)
logger.addHandler(console_handler)

# Flask application setup
app = Flask(__name__)


@app.route("/rolldice/<player>", methods=["GET"])
@app.route("/rolldice/", methods=["GET"])
def handle_roll_dice(player=None):
    result = roll_dice()

    if player:
        logger.info(f"{player} is rolling the dice: {result}")
    else:
        logger.info(f"Anonymous player is rolling the dice: {result}")

    return str(result)


def roll_dice():
    return random.randint(1, 6)


if __name__ == "__main__":
    app.run(host="0.0.0.0", port=8080)
