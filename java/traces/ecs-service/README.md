## Java Application Setup for ECS Service with OpenTelemetry

This document provides step-by-step instructions for setting up a Java application on Amazon ECS, using OpenTelemetry to send tracing data directly to your Logz.io account.

### **Prerequisites**

Before you begin, ensure you have the following prerequisites in place:

- AWS CLI configured with access to your AWS account.
- Docker installed for building images.
- AWS IAM role with sufficient permissions to create and manage ECS resources.
- Amazon ECR repository for storing the Docker images.
- Java JDK 11+ installed locally for development and testing.
- Maven or Gradle for building the Java project.

### **Architecture Overview**

This guide focuses on deploying the Java container using the following architecture:

```
project-root/
├── java-app/
│   ├── src/                       # Java application source code
│   ├── main/                      # Main Java files
│   │   └── java/
│   │       └── com/
│   │           └── example/
│   │               └── javaapp/
│   │                   ├── JavaAppApplication.java      # Main application class
│   │                   └── HelloController.java         # REST controller
│   ├── pom.xml                                          # Maven build configuration
│   ├── Dockerfile                                       # Dockerfile for Java application
│   └── opentelemetry-javaagent.jar                      # OpenTelemetry Java agent
├── ecs/
│   └── task-definition.json         # ECS task definition file
└── otel-collector    
     ├── collector-config.yaml        # OpenTelemetry Collector configuration
     └── Dockerfile                   # Dockerfile for the Collector
```

The Java application includes:

- **JavaAppApplication.java**: The main entry point for the Java Spring Boot application.
- **HelloController.java**: A simple REST controller providing two endpoints.
- **Dockerfile**: Used to create a Docker image for the Java application.
- **pom.xml**: Contains Maven build configuration and dependencies.
- **opentelemetry-javaagent.jar**: The OpenTelemetry Java agent for automatic instrumentation.

### **Code**

#### **JavaAppApplication.java**

```java
package com.example.javaapp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class JavaAppApplication {
    public static void main(String[] args) {
        SpringApplication.run(JavaAppApplication.class, args);
    }
}
```

This is the main entry point for the Spring Boot application.

#### **HelloController.java**

```java
package com.example.javaapp;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloController {

    @GetMapping("/")
    public String index() {
        return "Hello from the instrumented Java app!";
    }

    @GetMapping("/hello")
    public String hello(@RequestParam(value = "name", defaultValue = "World") String name) {
        return "Hello, " + name + "!";
    }
}
```

This REST controller provides two simple endpoints: `/` and `/hello`.

#### **Dockerfile**

```dockerfile
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

```

The Dockerfile uses a multi-stage build approach with a Maven image to build the application, and then a slim OpenJDK image to run it. It sets up the required files, sets environment variables for OpenTelemetry, and starts the application with the OpenTelemetry Java agent for tracing.

#### **pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
    https://maven.apache.org/xsd/maven-4.0.0.xsd">

    <modelVersion>4.0.0</modelVersion>
    <groupId>com.example</groupId>
    <artifactId>java-app</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <name>java-app</name>
    <description>Demo Java application with OpenTelemetry instrumentation</description>
    <packaging>jar</packaging>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>2.7.5</version>
        <relativePath/>
    </parent>

    <properties>
        <java.version>11</java.version>
    </properties>

    <dependencies>
        <!-- Spring Boot Web Starter -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>

        <!-- Optional: For health checks -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <!-- Spring Boot Maven Plugin -->
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <configuration>
                    <executable>true</executable>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

This file includes the Maven build configuration for the Java Spring Boot application.

#### **task-definition.json**

```json
{
  "family": "java-app-task",
  "networkMode": "awsvpc",
  "requiresCompatibilities": ["FARGATE"],
  "cpu": "256",
  "memory": "512",
  "executionRoleArn": "arn:aws:iam::<aws_account_id>:role/ecsTaskExecutionRole",
  "containerDefinitions": [
    {
      "name": "java-app",
      "image": "<aws_account_id>.dkr.ecr.<region>.amazonaws.com/java-app:latest",
      "cpu": 128,
      "portMappings": [
        {
          "containerPort": 8080,
          "protocol": "tcp"
        }
      ],
      "essential": true,      
      "environment": [],
      "logConfiguration": {
        "logDriver": "awslogs",
        "options": {
          "awslogs-group": "/ecs/java-app",
          "awslogs-region": "<aws-region>",
          "awslogs-stream-prefix": "ecs"
        }
      }
    },
    {
      "name": "otel-collector",
      "image": "<aws_account_id>.dkr.ecr.<aws-region>.amazonaws.com/otel-collector:latest",
      "cpu": 128,      
      "essential": false,
      "command": ["--config=/etc/collector-config.yaml"],
      "environment": [
        {
          "name": "LOGZIO_TRACING_TOKEN",
          "value": "<logzio_tracing_token>"
        },
        {
          "name": "LOGZIO_REGION",
          "value": "<logzio_region>"
        }
      ],
      "logConfiguration": {
        "logDriver": "awslogs",
        "options": {
          "awslogs-group": "/ecs/otel-collector",
          "awslogs-region": "<aws-region>",
          "awslogs-stream-prefix": "ecs"
        }
      }
    }
  ]
}
```

This task definition includes both the Java application container and the OpenTelemetry Collector container, defining their configurations and log groups.

### **Step-by-Step Instructions**

#### **1. Project Structure Setup**

Ensure the project structure follows the provided architecture. The Java application source code should be located in the `java-app/` directory.

#### **2. Create an Amazon ECR Repository**

Create an Amazon ECR repository to store the Docker image for the Java application:

```shell
aws ecr create-repository --repository-name java-app --region <aws-region>
```

#### **3. Configure OpenTelemetry Collector**

The `collector-config.yaml` in the `ecs/` directory defines the OpenTelemetry Collector configuration for receiving, processing, and exporting telemetry data. The Java application will use OpenTelemetry instrumentation to send traces to the collector running as a sidecar in the ECS task.

**collector-config.yaml**

```yaml
receivers:
  otlp:
    protocols:
      grpc:
        endpoint: "0.0.0.0:4317"
      http:
        endpoint: "0.0.0.0:4318"

exporters:
  logzio/traces:
    account_token: "<<TRACING-SHIPPING-TOKEN>>"
    region: "<<LOGZIO_ACCOUNT_REGION_CODE>>"
    headers:
      user-agent: logzio-opentelemetry-traces

processors:
  batch:
  tail_sampling:
    policies:
      [
        {
          name: policy-errors,
          type: status_code,
          status_code: {status_codes: [ERROR]}
        },
        {
          name: policy-slow,
          type: latency,
          latency: {threshold_ms: 1000}
        }, 
        {
          name: policy-random-ok,
          type: probabilistic,
          probabilistic: {sampling_percentage: 10}
        }        
      ]

extensions:
  pprof:
    endpoint: :1777
  zpages:
    endpoint: :55679
  health_check:

service:
  extensions: [health_check, pprof, zpages]
  pipelines:
    traces:
      receivers: [otlp]
      processors: [tail_sampling, batch]
      exporters: [logzio/traces]
  telemetry:
    logs:
      level: info
```

This configuration file defines the OpenTelemetry Collector, specifying how to receive, process, and export traces to Logz.io.

**Dockerfile**

```shell
# Dockerfile for OpenTelemetry Collector
FROM otel/opentelemetry-collector-contrib:latest
COPY collector-config.yaml /etc/collector-config.yaml
CMD ["--config", "/etc/collector-config.yaml"]
```

#### **4. Build and Push the Docker Image**

To build the Docker image for the Java application and opentelemetry collector, use the following commands:

```shell
cd java-app/
docker build --platform linux/amd64 -t java-app:latest .

cd otel-collector/
docker build --platform linux/amd64 -t otel-collector:latest .
```

Next, push the image to your Amazon ECR repository:

```shell
# Authenticate Docker to your Amazon ECR repository
aws ecr get-login-password --region <aws-region> | docker login --username AWS --password-stdin <aws_account_id>.dkr.ecr.<region>.amazonaws.com

# Tag and push the images
docker tag java-app:latest <aws_account_id>.dkr.ecr.<region>.amazonaws.com/java-app:latest
docker push <aws_account_id>.dkr.ecr.<region>.amazonaws.com/java-app:latest

docker tag otel-collector:latest <aws_account_id>.dkr.ecr.<region>.amazonaws.com/otel-collector:latest
docker push <aws_account_id>.dkr.ecr.<region>.amazonaws.com/otel-collector:latest
```

#### **5. Set Up CloudWatch Log Groups**

- **Log Group Creation**: Create log groups for your Java application and OpenTelemetry Collector in CloudWatch.

```shell
aws logs create-log-group --log-group-name /ecs/java-app
aws logs create-log-group --log-group-name /ecs/otel-collector
```

- Ensure the ECS task definition is configured to send logs to the appropriate log groups using the `awslogs` log driver.

#### **6. Create an ECS Cluster and Service**

- **Create ECS Cluster**: Create an ECS cluster using the following command:

```shell
aws ecs create-cluster --cluster-name <cluster-name> --region <aws-region>
```

- **Create ECS Service**: Use the ECS cluster to create a service based on the registered task definition.

```shell
aws ecs create-service \
  --cluster <cluster-name> \
  --service-name <service-name> \
  --task-definition java-app-task \
  --desired-count 1 \
  --launch-type FARGATE \
  --network-configuration "awsvpcConfiguration={subnets=[\"YOUR_SUBNET_ID\"],securityGroups=[\"YOUR_SECURITY_GROUP_ID\"],assignPublicIp=ENABLED}" \
  --region <aws-region> \
```
- **Register Task Definition**: Use the `task-definition.json` file located in the `ecs/` directory to register a new task definition for your Java application.

```shell
aws ecs register-task-definition --cli-input-json file://ecs/task-definition.json
```

#### **7. Update ECS Service**

After making changes to the container or ECS configuration, update your ECS service to force a new deployment and pull the latest image:

```shell
aws ecs update-service \
  --cluster <cluster-name> \
  --service-name java-app-service \
  --force-new-deployment \
  --region <aws-region>
```

#### **8. Send Requests to the Application**

To verify that the application is working and traces are being collected, use `curl` or a web browser to send requests to the Java application:

```shell
curl http://<public-ip>:8080/
curl http://<public-ip>:8080/hello
```

### **Create Cluster and Service, Update Services**

Ensure you have created the ECS cluster and registered the service with the correct task definition. Whenever updates are made (e.g., new Docker image versions or configuration changes), force a new deployment to apply the changes.