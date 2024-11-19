FROM python:3.8-slim

WORKDIR /app

COPY . .

RUN pip install --no-cache-dir -r requirements.txt

# Set environment variables for OpenTelemetry configuration
ENV OTEL_TRACES_SAMPLER=always_on
ENV OTEL_EXPORTER_OTLP_ENDPOINT="http://localhost:4317"
ENV OTEL_RESOURCE_ATTRIBUTES="service.name=python-app"

EXPOSE 5000

CMD ["opentelemetry-instrument", "python", "app.py"]
