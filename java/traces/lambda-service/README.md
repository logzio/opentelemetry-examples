# Guide: Auto-Instrumenting a Java AWS Lambda Function with OpenTelemetry and Sending Traces to Logz.io

This guide will walk you through the steps to auto-instrument a Java AWS Lambda function using OpenTelemetry and send the generated traces to Logz.io. We will:

1. Create a simple Java Lambda function.
2. Configure the OpenTelemetry Collector.
3. Set up the Maven project.
4. Build and package the Lambda function.
5. Prepare the deployment package.
6. Deploy and configure the Lambda function in AWS.
7. Enable auto-instrumentation and active tracing.
8. Test the function and verify traces in Logz.io.

---

## Prerequisites

- **AWS Account**: Access to AWS Lambda and permissions to create functions and layers.
- **Java Development Kit (JDK)**: Version 11 or higher. (Java 21 is not yet supported)
- **Maven**: For building the Java project.
- **AWS CLI**: Optional, for deploying via the command line.

---

## Step 1: Create the Java Lambda Function Code

Create a Java class for your Lambda function. We'll use a simple function that makes an HTTP GET request and logs some information.

### **File:** `LambdaFunction.java`

```java
package example;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.LambdaLogger;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.HashMap;

import com.fasterxml.jackson.databind.ObjectMapper;

public class LambdaFunction implements RequestHandler<Map<String, Object>, Map<String, Object>> {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Map<String, Object> handleRequest(Map<String, Object> event, Context context) {
        LambdaLogger logger = context.getLogger();

        // Simulate a traceable operation
        logger.log("Processing request...\n");

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://httpbin.org/get"))
                .GET()
                .build();

        Map<String, Object> responseMap = new HashMap<>();

        try {
            logger.log("Making an HTTP GET request to https://httpbin.org/get\n");
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            logger.log("Request succeeded with status: " + response.statusCode() + "\n");

            // Prepare response body
            Map<String, Object> responseBody = new HashMap<>();
            responseBody.put("message", "Hello, this Lambda is instrumented with OpenTelemetry!");
            responseBody.put("http_status_code", response.statusCode());
            responseBody.put("http_response", objectMapper.readValue(response.body(), Map.class));
            responseBody.put("input_event", event);

            String responseBodyString = objectMapper.writeValueAsString(responseBody);

            // Prepare full response
            responseMap.put("statusCode", 200);
            responseMap.put("body", responseBodyString);

        } catch (IOException | InterruptedException e) {
            logger.log("HTTP request failed: " + e.toString() + "\n");

            Map<String, Object> errorBody = new HashMap<>();
            errorBody.put("message", "HTTP request failed");
            errorBody.put("error", e.toString());

            String errorBodyString;
            try {
                errorBodyString = objectMapper.writeValueAsString(errorBody);
            } catch (IOException ioException) {
                logger.log("Failed to serialize error body: " + ioException.toString() + "\n");
                errorBodyString = "{\"message\":\"Internal server error\"}";
            }

            responseMap.put("statusCode", 500);
            responseMap.put("body", errorBodyString);
        }

        return responseMap;
    }
}
```

---

## Step 2: Configure the OpenTelemetry Collector

Create a configuration file for the OpenTelemetry Collector. This collector will receive traces from the OpenTelemetry Java agent and export them to Logz.io.

### **File:** `collector-config.yaml`

```yaml
receivers:
  otlp:
    protocols:
      grpc:
        endpoint: "0.0.0.0:4317"
      http:
        endpoint: "0.0.0.0:4318"

exporters:
  logzio:
    account_token: "<Your Logz.io Tracing Token>"
    region: "<Your Region Code>" 
    compression: gzip
    headers:
      user-agent: logzio-opentelemetry-traces

processors:
  batch:

service:
  pipelines:
    traces:
      receivers: [otlp]
      processors: [batch]
      exporters: [logzio]
  telemetry:
    logs:
      level: debug
```

**Note:**

- Replace `<Your Logz.io Tracing Token>` with your actual Logz.io tracing token.
- Replace `<Your Region Code>` with your Logz.io region code.

---

## Step 3: Set Up the Maven Project

Create a `pom.xml` file to manage your project's dependencies and build process.

### **File:** `pom.xml`

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                             https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>example</groupId>
    <artifactId>lambda-function</artifactId>
    <version>1.0-SNAPSHOT</version>
    <packaging>jar</packaging>

    <properties>
        <java.version>17</java.version>
    </properties>

    <dependencies>
        <!-- AWS Lambda dependencies -->
        <dependency>
            <groupId>com.amazonaws</groupId>
            <artifactId>aws-lambda-java-core</artifactId>
            <version>1.2.2</version>
        </dependency>

        <!-- Jackson for JSON processing -->
        <dependency>
            <groupId>com.fasterxml.jackson.core</groupId>
            <artifactId>jackson-databind</artifactId>
            <version>2.15.2</version>
        </dependency>

        <!-- Jackson annotations -->
        <dependency>
            <groupId>com.fasterxml.jackson.core</groupId>
            <artifactId>jackson-annotations</artifactId>
            <version>2.15.2</version>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <!-- Maven Shade Plugin to create an uber-JAR -->
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-shade-plugin</artifactId>
                <version>3.4.1</version>
                <executions>
                    <execution>
                        <phase>package</phase>
                        <goals>
                            <goal>shade</goal>
                        </goals>
                        <configuration>
                            <createDependencyReducedPom>false</createDependencyReducedPom>
                            <filters>
                                <filter>
                                    <artifact>*:*</artifact>
                                    <excludes>
                                        <exclude>META-INF/*.SF</exclude>
                                        <exclude>META-INF/*.DSA</exclude>
                                        <exclude>META-INF/*.RSA</exclude>
                                    </excludes>
                                </filter>
                            </filters>
                        </configuration>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
</project>
```

---

## Step 4: Organize the Project Structure

Ensure your project directory is structured correctly.

```
your-project/
├── src/
│   └── main/
│       └── java/
│           └── example/
│                 └── LambdaFunction.java
├── collector-config.yaml
└── pom.xml
```

---

## Step 5: Build and Package the Lambda Function

Use Maven to build your project and create an uber-JAR that includes all dependencies.

### **Build the Project**

Open a terminal in the root directory of your project and run:

```bash
mvn clean package
```

This will generate a JAR file in the `target/` directory, e.g., `lambda-function-1.0-SNAPSHOT.jar`.

---

## Step 6: Prepare the Deployment Package

AWS Lambda requires your deployment package to include your function code and any additional files (like the collector configuration).

### **Create a `lib` Directory**

Create a directory called `lib` and copy the generated JAR file into it.

```bash
mkdir lib
cp target/lambda-function-1.0-SNAPSHOT.jar lib/
```

### **Prepare the Deployment Package**

Zip the `lib` directory and the `collector-config.yaml` file into a deployment package.

#### **For Linux or macOS**

In the root directory of your project, run:

```bash
zip -r lambda-deployment-package.zip lib collector-config.yaml
```

#### **For Windows**

Create a ZIP file containing the `lib` folder and `collector-config.yaml`.

**Ensure** that the ZIP file does not contain any parent directories. The structure inside the ZIP file should be:

```
lambda-deployment-package.zip
├── lib/
│   └── lambda-function-1.0-SNAPSHOT.jar
└── collector-config.yaml
```

---

## Step 7: Deploy and Configure the Lambda Function in AWS

### **Create a New Lambda Function**

1. **Go to AWS Lambda Console**: Navigate to the AWS Lambda console.
2. **Create Function**: Click on **Create function**.
3. **Choose "Author from scratch"**:
    - **Function name**: `MyJavaLambdaFunction` (or any name you prefer).
    - **Runtime**: `Java 11 (Corretto)` or `Java 17 (Corretto)` (ensure it matches your `java.version` in `pom.xml`).
    - **Permissions**: Choose or create an execution role with necessary permissions.
4. **Create Function**: Click **Create function**.

### **Upload the Deployment Package**

1. **Under "Code source"**, select **Upload from > .zip file**.
2. **Upload ZIP File**: Click **Upload** and select your `lambda-deployment-package.zip` file.
3. **Save**: Click **Save** to upload the code.

### **Set the Handler**

1. **Under "Runtime settings"**, click **Edit**.
2. **Handler**: Set the handler to `example.LambdaFunction::handleRequest`.
3. **Save**: Click **Save**.

### **Add the OpenTelemetry Layers**

#### **Add the OpenTelemetry Java Agent Layer**

1. **In the "Layers" section**, click **Add a layer**.
2. **Choose a layer**: Select **Specify an ARN**.
3. **Layer ARN**: Enter the ARN for the OpenTelemetry Java agent layer:

   ```
   arn:aws:lambda:<region>:901920570463:layer:aws-otel-java-agent-ver-1-29-0:1
   ```

   **Replace `<region>`** with your AWS region code (e.g., `us-east-1`).

4. **Add**: Click **Add**.

#### **Add the OpenTelemetry Collector Layer**

1. **In the "Layers" section**, click **Add a layer**.
2. **Choose a layer**: Select **Specify an ARN**.
3. **Layer ARN**: Enter the ARN for the Logz.io OpenTelemetry Collector layer:

    - For **x86_64** architecture:

      ```
      arn:aws:lambda:<region>:486140753397:layer:logzio-opentelemetry-collector-amd64:1
      ```

    - For **arm64** architecture:

      ```
      arn:aws:lambda:<region>:486140753397:layer:logzio-opentelemetry-collector-arm64:1
      ```

   **Replace `<region>`** with your AWS region code.

4. **Add**: Click **Add**.

### **Set the Environment Variables**

1. **Scroll down** to the **"Environment variables"** section.
2. **Click "Edit"** and add the following environment variables:

   | Key                                 | Value                                  |
      |-------------------------------------|----------------------------------------|
   | `AWS_LAMBDA_EXEC_WRAPPER`           | `/opt/otel-handler`                    |
   | `OPENTELEMETRY_COLLECTOR_CONFIG_URI`| `/var/task/collector-config.yaml`      |
   | `OTEL_JAVA_AGENT_FAST_STARTUP_ENABLED` | `true`                             |
   | `OTEL_RESOURCE_ATTRIBUTES`          | `service.name=MyJavaLambdaFunction`    |

   **Note:**

    - `service.name` can be any name you choose; it identifies your service in Logz.io.
    - You can add additional attributes as needed.

3. **Save**: Click **Save**.

### **Enable Active Tracing**

1. **Navigate** to the **"Configuration"** tab.
2. **Click** on **"Monitoring and operations tools"**.
3. **Click "Edit"**.
4. **Enable** **Active tracing** by checking the box.
5. **Save**: Click **Save**.

---

## Step 8: Test the Lambda Function

### **Create a Test Event**

1. **In the Lambda console**, click **Test**.
2. **Configure test event**:
    - **Event name**: `TestEvent`.
    - **Template**: Select **"Hello World"** or leave the default JSON.
3. **Create**: Click **Save**.

### **Invoke the Function**

1. **Click** **Test** to invoke the function.
2. **Check the Execution Results**: You should see a successful execution with a response.

---

## Step 9: Verify Traces in Logz.io

- **Wait a Few Minutes**: It may take a few minutes for traces to appear in your Logz.io account.
- **Log in to Logz.io**: Navigate to the tracing section.
- **Find Your Service**: Look for `MyJavaLambdaFunction` or the service name you set in the environment variables.
- **View Traces**: You should see the traces generated by your Lambda function's execution.