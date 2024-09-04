package com.logzio.otel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class DiceApplication {

    private static final Logger logger = LoggerFactory.getLogger(DiceApplication.class);

    public static void main(String[] args) {
        try {
            // Initialize OpenTelemetry
            OpenTelemetryConfig config = new OpenTelemetryConfig();
            config.initializeOpenTelemetry();
        } catch (Exception e) {
            logger.error("Error initializing OpenTelemetry: ", e);
        }

        // Start the Spring Boot application
        SpringApplication app = new SpringApplication(DiceApplication.class);
        app.setBannerMode(Banner.Mode.OFF);
        app.run(args);
    }
}
