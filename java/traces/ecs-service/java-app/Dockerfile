# Use a Maven image to build the application
FROM maven:3.8.6-openjdk-11-slim AS builder

# Set the working directory
WORKDIR /app

# Copy the project files
COPY pom.xml .
COPY src ./src

# Package the application
RUN mvn clean package -DskipTests

# Use a lightweight OpenJDK image for the runtime
FROM openjdk:11-jre-slim

# Set the working directory
WORKDIR /app

# Copy the packaged application and the OpenTelemetry agent
COPY --from=builder /app/target/java-app-0.0.1-SNAPSHOT.jar app.jar
COPY opentelemetry-javaagent.jar opentelemetry-javaagent.jar

# Expose the application port
EXPOSE 8080

# Set environment variables for OpenTelemetry
ENV OTEL_TRACES_SAMPLER=always_on
ENV OTEL_EXPORTER_OTLP_ENDPOINT="http://localhost:4318"
ENV OTEL_RESOURCE_ATTRIBUTES="service.name=java-app"

# Start the application with the OpenTelemetry Java agent
ENTRYPOINT ["java", "-javaagent:/app/opentelemetry-javaagent.jar", "-jar", "app.jar"]
