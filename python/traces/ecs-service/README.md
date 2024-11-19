### Python Application Setup for ECS Service with OpenTelemetry

This document provides step-by-step instructions for setting up a Python application on Amazon ECS, using OpenTelemetry to send tracing data directly to your Logz.io account.

#### **Prerequisites**

Before you begin, ensure you have the following prerequisites in place:

- AWS CLI configured with access to your AWS account.
- Docker installed for building images.
- AWS IAM role with sufficient permissions to create and manage ECS resources.
- Amazon ECR repository for storing the Docker images.
- Python 3.x and pip installed locally for development and testing.

#### **Architecture Overview**

This guide focuses on deploying the Python container using the following architecture:

```
project-root/
├── python-app/
│   ├── app.py                       # Python application code
│   ├── Dockerfile                   # Dockerfile for Python application
│   └── requirements.txt             # Python dependencies, includes OpenTelemetry
├── ecs/
│   └── task-definition.json         # ECS task definition file
└── otel-collector    
     ├── collector-config.yaml        # OpenTelemetry Collector configuration
     └── Dockerfile                   # Dockerfile for the Collector
```

The Python application includes:

- **app.py**: A simple Python application using Flask, instrumented with OpenTelemetry for distributed tracing.
- **Dockerfile**: Used to create a Docker image for the Python application.
- **requirements.txt**: Lists the required Python dependencies, including OpenTelemetry for tracing.

#### **Code**

##### **app.py**

```python
from flask import Flask, request

app = Flask(__name__)

@app.route('/')
def index():
    return "Hello from the instrumented Python app!"

@app.route('/hello')
def hello():
    name = request.args.get('name', 'World')
    return f"Hello, {name}!"

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=5000)
```

The above code sets up a simple Flask application.

#### **Dockerfile**

```dockerfile
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

```

The Dockerfile uses a slim Python image, installs the necessary dependencies, sets the environment variables required for OpenTelemetry configuration, and starts the application with OpenTelemetry instrumentation.

##### **requirements.txt**

```
flask
opentelemetry-distro
opentelemetry-exporter-otlp
opentelemetry-instrumentation-flask
```

This file lists the required dependencies, including Flask and OpenTelemetry packages for tracing. The `opentelemetry-instrumentation-flask` package is used to automatically instrument Flask applications, enabling tracing for incoming HTTP requests without requiring manual instrumentation of each route.

##### **task-definition.json**

```json
{
  "family": "python-app-task",
  "networkMode": "awsvpc",
  "requiresCompatibilities": ["FARGATE"],
  "cpu": "256",
  "memory": "512",
  "executionRoleArn": "arn:aws:iam::<aws_account_id>:role/ecsTaskExecutionRole",
  "containerDefinitions": [
    {
      "name": "python-app",
      "image": "<aws_account_id>.dkr.ecr.<region>.amazonaws.com/python-app:latest",
      "cpu": 128,
      "portMappings": [
        {
          "containerPort": 5000,
          "protocol": "tcp"
        }
      ],
      "essential": true,      
      "environment": [],
      "logConfiguration": {
        "logDriver": "awslogs",
        "options": {
          "awslogs-group": "/ecs/python-app",
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

This task definition includes both the Python application container and the OpenTelemetry Collector container, defining their configurations and log groups.

#### **Step-by-Step Instructions**

##### **1. Project Structure Setup**

Ensure the project structure follows the provided architecture. The Python application source code should be located in the `python-app/` directory.

##### **2. Create an Amazon ECR Repository**

Create an Amazon ECR repository to store the Docker image for the Python application:

```shell
aws ecr create-repository --repository-name python-app --region <aws-region>
```

##### **3. Configure OpenTelemetry Collector**

The `collector-config.yaml` in the `ecs/` directory defines the OpenTelemetry Collector configuration for receiving, processing, and exporting telemetry data. The Python application will use OpenTelemetry instrumentation to send traces to the collector running as a sidecar in the ECS task.

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

To build the Docker image for the Python application and opentelemetry collector, use the following commands:

```shell
cd python-app/
docker build --platform linux/amd64 -t python-app:latest .

cd otel-collector/
docker build --platform linux/amd64 -t otel-collector:latest .
```

Next, push the images to your Amazon ECR repository:

```shell
# Authenticate Docker to your Amazon ECR repository
aws ecr get-login-password --region <aws-region> | docker login --username AWS --password-stdin <aws_account_id>.dkr.ecr.<region>.amazonaws.com

# Tag and push the images
docker tag python-app:latest <aws_account_id>.dkr.ecr.<region>.amazonaws.com/python-app:latest
docker push <aws_account_id>.dkr.ecr.<region>.amazonaws.com/python-app:latest

docker tag otel-collector:latest <aws_account_id>.dkr.ecr.<region>.amazonaws.com/otel-collector:latest
docker push <aws_account_id>.dkr.ecr.<region>.amazonaws.com/otel-collector:latest
```

##### **5. Set Up CloudWatch Log Groups**

- **Log Group Creation**: Create log groups for your Python application and OpenTelemetry Collector in CloudWatch.

```shell
aws logs create-log-group --log-group-name /ecs/python-app
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
  --task-definition python-app-task \
  --desired-count 1 \
  --launch-type FARGATE \
  --network-configuration "awsvpcConfiguration={subnets=[\"YOUR_SUBNET_ID\"],securityGroups=[\"YOUR_SECURITY_GROUP_ID\"],assignPublicIp=ENABLED}" \
  --region <aws-region> \
```

- **Register Task Definition**: Use the `task-definition.json` file located in the `ecs/` directory to register a new task definition for your Python application.

```shell
aws ecs register-task-definition --cli-input-json file://ecs/task-definition.json
```

##### **7. Update ECS Service**

After making changes to the container or ECS configuration, update your ECS service to force a new deployment and pull the latest image:

```shell
aws ecs update-service \
  --cluster <cluster-name> \
  --service-name python-app-service \
  --force-new-deployment \
  --region <aws-region>
```

##### **8. Send Requests to the Application**

To verify that the application is working and traces are being collected, use `curl` or a web browser to send requests to the Python application:

```shell
curl http://<public-ip>:5000/
curl http://<public-ip>:5000/hello
```

#### **Create Cluster and Service, Update Services**

Ensure you have created the ECS cluster and registered the service with the correct task definition. Whenever updates are made (e.g., new Docker image versions or configuration changes), force a new deployment to apply the changes.

