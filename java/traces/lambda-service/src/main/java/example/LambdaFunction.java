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

            String errorBodyString = "";
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
