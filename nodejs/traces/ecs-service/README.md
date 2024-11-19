### Node.js Application Setup for ECS Service with OpenTelemetry

This document provides step-by-step instructions for setting up a Node.js application on Amazon ECS, using OpenTelemetry to send tracing data directly to your Logz.io account.

#### **Prerequisites**

Before you begin, ensure you have the following prerequisites in place:

- AWS CLI configured with access to your AWS account.
- Docker installed for building images.
- AWS IAM role with sufficient permissions to create and manage ECS resources.
- Amazon ECR repository for storing the Docker images.
- Node.js and npm installed locally for development and testing.

#### **Architecture Overview**

This guide focuses on deploying the Node.js container using the following architecture:

```
project-root/
├── nodejs-app/
│   ├── app.js                       # Node.js application code
│   ├── tracing.js                   # OpenTelemetry tracing setup for Node.js
│   ├── Dockerfile                   # Dockerfile for Node.js application
│   └── package.json                 # Node.js dependencies, includes OpenTelemetry
├── ecs/
│   └── task-definition.json         # ECS task definition file
└── otel-collector    
     ├── collector-config.yaml        # OpenTelemetry Collector configuration
     └── Dockerfile                   # Dockerfile for the Collector

```

The Node.js application includes:

- **app.js**: A simple Node.js application using Express, with manual OpenTelemetry instrumentation.
- **tracing.js**: Sets up the OpenTelemetry tracing for the application.
- **Dockerfile**: Used to create a Docker image for the Node.js application.
- **package.json**: Lists the required Node.js dependencies, including OpenTelemetry for tracing.

#### **Code**

##### **app.py**
```javascript 
// app.js

const express = require('express');
const { startTracing } = require('./tracing');

// Start tracing before any other code
startTracing();

// Import the OpenTelemetry API
const { trace } = require('@opentelemetry/api');

// Get a tracer
const tracer = trace.getTracer('nodejs-app');

const app = express();
const port = 3000;

// Middleware to create a root span for each request
app.use((req, res, next) => {
  const span = tracer.startSpan(`HTTP ${req.method} ${req.path}`);
  // Attach the span to the request object so we can use it in routes
  req.span = span;
  // Ensure the span ends when the response is finished
  res.on('finish', () => {
    span.end();
  });
  next();
});

app.get('/', (req, res) => {
  // Use the span from the middleware
  const span = req.span;
  span.addEvent('Handling / request');
  res.send('Hello from the instrumented Node.js app!');
});

app.get('/hello', (req, res) => {
  const span = req.span;
  span.addEvent('Handling /hello request');
  const name = req.query.name || 'World';

  // Start a child span for some operation (e.g., processing data)
  const childSpan = tracer.startSpan('processData', {
    parent: span,
  });
  // Simulate some processing
  childSpan.addEvent('Processing data');
  // ... your processing logic here
  childSpan.end();

  res.send(`Hello, ${name}!`);
});

app.listen(port, () => {
  console.log(`Node.js app listening at http://localhost:${port}`);
});
```

The above code sets up a simple Express application with middleware that automatically creates spans for each incoming HTTP request and uses OpenTelemetry for tracing, allowing you to trace the request lifecycle more effectively.

##### **tracing.js**

```javascript
'use strict';

const { NodeSDK } = require('@opentelemetry/sdk-node');
const { OTLPTraceExporter } = require('@opentelemetry/exporter-trace-otlp-grpc');
const { diag, DiagConsoleLogger, DiagLogLevel } = require('@opentelemetry/api');
const { Resource } = require('@opentelemetry/resources');

// Optional: Enable diagnostic logging for debugging
diag.setLogger(new DiagConsoleLogger(), DiagLogLevel.INFO);

function startTracing() {
  const traceExporter = new OTLPTraceExporter({
    url: process.env.OTEL_EXPORTER_OTLP_ENDPOINT || 'grpc://localhost:4317',
  });

  const sdk = new NodeSDK({
    traceExporter,
    resource: new Resource({
      'service.name': 'nodejs-app',
    }),
  });

  sdk.start()
    .then(() => console.log('Tracing initialized'))
    .catch((error) => console.log('Error initializing tracing', error));

  // Optional: Gracefully shutdown tracing on process exit
  process.on('SIGTERM', () => {
    sdk.shutdown()
      .then(() => console.log('Tracing terminated'))
      .catch((error) => console.log('Error terminating tracing', error))
      .finally(() => process.exit(0));
  });
}

module.exports = { startTracing };
```

The `tracing.js` file initializes OpenTelemetry tracing, configuring the OTLP exporter to send trace data to the OpenTelemetry Collector.

##### **Dockerfile**

```dockerfile
# Use Node.js LTS version
FROM node:16-alpine

# Set the working directory
WORKDIR /app

# Copy package.json and package-lock.json
COPY package*.json ./

# Install dependencies
RUN npm install --production

# Copy the application code
COPY . .

# Expose the application port
EXPOSE 3000

# Set environment variables for OpenTelemetry
ENV OTEL_TRACES_SAMPLER=always_on
ENV OTEL_EXPORTER_OTLP_ENDPOINT="http://localhost:4317"
ENV OTEL_RESOURCE_ATTRIBUTES="service.name=nodejs-app"

# Start the application
CMD ["npm", "start"]
```

The Dockerfile uses an Alpine-based Node.js image, installs the necessary dependencies, sets the environment variables required for OpenTelemetry configuration, and starts the application.

##### **package.json**

```json
{
  "name": "nodejs-app",
  "version": "1.0.0",
  "description": "Demo Node.js application with OpenTelemetry instrumentation",
  "main": "app.js",
  "scripts": {
    "start": "node app.js"
  },
  "keywords": [],
  "author": "",
  "license": "ISC",
  "dependencies": {
    "@opentelemetry/api": "^1.9.0",
    "@opentelemetry/exporter-trace-otlp-grpc": "^0.52.1",
    "@opentelemetry/sdk-node": "^0.52.1",
    "express": "^4.21.1"
  }
}

```

This file lists the required dependencies, including Express and OpenTelemetry packages for tracing.

##### **task-definition.json**

```json
{
  "family": "nodejs-app-task",
  "networkMode": "awsvpc",
  "requiresCompatibilities": ["FARGATE"],
  "cpu": "256",
  "memory": "512",
  "executionRoleArn": "arn:aws:iam::<aws_account_id>:role/ecsTaskExecutionRole",
  "containerDefinitions": [
    {
      "name": "nodejs-app",
      "image": "<aws_account_id>.dkr.ecr.<region>.amazonaws.com/nodejs-app:latest",
      "cpu": 128,
      "portMappings": [
        {
          "containerPort": 3000,
          "protocol": "tcp"
        }
      ],
      "essential": true,
      "environment": [],
      "logConfiguration": {
        "logDriver": "awslogs",
        "options": {
          "awslogs-group": "/ecs/nodejs-app",
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

This task definition includes both the Node.js application container and the OpenTelemetry Collector container, defining their configurations and log groups.

#### **Step-by-Step Instructions**

##### **1. Project Structure Setup**

Ensure the project structure follows the provided architecture. The Node.js application source code should be located in the `nodejs-app/` directory.

##### **2. Create an Amazon ECR Repository**

Create an Amazon ECR repository to store the Docker image for the Node.js application:

```shell
aws ecr create-repository --repository-name nodejs-app --region <aws-region>
```

##### **3. Configure OpenTelemetry Collector**

The `collector-config.yaml` in the `ecs/` directory defines the OpenTelemetry Collector configuration for receiving, processing, and exporting telemetry data. The Node.js application will use OpenTelemetry instrumentation to send traces to the collector running as a sidecar in the ECS task.

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

##### **4. Build and Push the Docker Image**

To build the Docker image for the Node.js application and OpenTelemetry Collector, use the following commands:

```shell
cd nodejs-app/
docker build --platform linux/amd64 -t nodejs-app:latest .

cd otel-collector/
docker build --platform linux/amd64 -t otel-collector:latest .
```

Next, push the images to your Amazon ECR repository:

```shell
# Authenticate Docker to your Amazon ECR repository
aws ecr get-login-password --region <aws-region> | docker login --username AWS --password-stdin <aws_account_id>.dkr.ecr.<region>.amazonaws.com

# Tag and push the images
docker tag nodejs-app:latest <aws_account_id>.dkr.ecr.<region>.amazonaws.com/nodejs-app:latest
docker push <aws_account_id>.dkr.ecr.<region>.amazonaws.com/nodejs-app:latest

docker tag otel-collector:latest <aws_account_id>.dkr.ecr.<region>.amazonaws.com/otel-collector:latest
docker push <aws_account_id>.dkr.ecr.<region>.amazonaws.com/otel-collector:latest
```

##### **5. Set Up CloudWatch Log Groups**

- **Log Group Creation**: Create log groups for your Node.js application and OpenTelemetry Collector in CloudWatch.

```shell
aws logs create-log-group --log-group-name /ecs/nodejs-app
aws logs create-log-group --log-group-name /ecs/otel-collector
```

- Ensure the ECS task definition is configured to send logs to the appropriate log groups using the `awslogs` log driver.

##### **6. Create an ECS Cluster and Service**

- **Create ECS Cluster**: Create an ECS cluster using the following command:

```shell
aws ecs create-cluster --cluster-name app-cluster --region <aws-region>
```

- **Create ECS Service**: Use the ECS cluster to create a service based on the registered task definition.

```shell
aws ecs create-service \
  --cluster <cluster-name> \
  --service-name <service-name> \
  --task-definition nodejs-app-task \
  --desired-count 1 \
  --launch-type FARGATE \
  --network-configuration "awsvpcConfiguration={subnets=[\"YOUR_SUBNET_ID\"],securityGroups=[\"YOUR_SECURITY_GROUP_ID\"],assignPublicIp=ENABLED}" \
  --region <aws-region> \
```

- **Register Task Definition**: Use the `task-definition.json` file located in the `ecs/` directory to register a new task definition for your Node.js application.

```shell
aws ecs register-task-definition --cli-input-json file://ecs/task-definition.json
```

##### **7. Update ECS Service**

After making changes to the container or ECS configuration, update your ECS service to force a new deployment and pull the latest image:

```shell
aws ecs update-service \
  --cluster <cluster-name> \
  --service-name nodejs-app-service \
  --force-new-deployment \
  --region <aws-region>
```

##### **8. Send Requests to the Application**

To verify that the application is working and traces are being collected, use `curl` or a web browser to send requests to the Node.js application:

```shell
curl http://<public-ip>:3000/
curl http://<public-ip>:3000/hello
```

#### **Create Cluster and Service, Update Services**

Ensure you have created the ECS cluster and registered the service with the correct task definition. Whenever updates are made (e.g., new Docker image versions or configuration changes), force a new deployment to apply the changes.
```

Act as a tech writer. I want you to generalize this so it will give user nodejs Application Setup for ECS Service with OpenTelemetry but with no specific application and redundent code. I think the approach should be how to take an application you wrote and launch it to ECS together with otel ofr sendin the traces to logzio.
Write it out please