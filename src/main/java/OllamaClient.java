package com.example;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class OllamaClient {

    private static final Logger logger = LoggerFactory.getLogger(OllamaClient.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final HttpClient httpClient;
    private final String model;
    private final String apiUrl;
    private final int maxRetries;
    private final long retryDelayMs;
    private final Duration requestTimeout;

    public OllamaClient(String model, String apiUrl, int maxRetries, long retryDelayMs, long requestTimeoutMinutes) {
        this.model = model;
        this.apiUrl = apiUrl;
        this.maxRetries = maxRetries;
        this.retryDelayMs = retryDelayMs;
        this.requestTimeout = Duration.ofMinutes(requestTimeoutMinutes);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10)) // Connection timeout
                .build();
    }

    public String extractTerms(String systemPrompt, String userPrompt) {
        String requestBody = buildChatRequestBody(systemPrompt, userPrompt);
        int attempt = 0;
        while (attempt < maxRetries) {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(apiUrl))
                        .header("Content-Type", "application/json")
                        .timeout(this.requestTimeout)
                        .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    return extractTextFromResponse(response.body());
                } else {
                    logger.warn("Attempt {}/{} failed. Received non-2xx status code: {}. Body: {}",
                            attempt + 1, maxRetries, response.statusCode(), response.body());
                }
            } catch (IOException | InterruptedException e) {
                logger.warn("Attempt {}/{} failed with exception: {}", attempt + 1, maxRetries, e.getMessage());
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                    return "";
                }
            }

            attempt++;
            if (attempt < maxRetries) {
                try {
                    Thread.sleep(retryDelayMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    logger.error("Retry delay was interrupted.", e);
                    return "";
                }
            }
        }
        logger.error("All {} attempts to contact Ollama failed for prompt.", maxRetries);
        return "";
    }

    private String buildChatRequestBody(String systemPrompt, String userPrompt) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", this.model);
        body.put("stream", false); // We want the full response at once, not a stream

        // Add options to make the model's output more deterministic and focused for extraction
        ObjectNode options = body.putObject("options");
        options.put("temperature", 0.0);
        options.put("top_p", 0.1);

        ArrayNode messages = body.putArray("messages");
        
        ObjectNode systemMessage = messages.addObject();
        systemMessage.put("role", "system");
        systemMessage.put("content", systemPrompt);

        ObjectNode userMessage = messages.addObject();
        userMessage.put("role", "user");
        userMessage.put("content", userPrompt);

        return body.toString();
    }

    private String extractTextFromResponse(String responseBody) {
        try {
            JsonNode responseNode = objectMapper.readTree(responseBody); // First, parse the main Ollama response wrapper
            String content = responseNode.path("message").path("content").asText("").trim();
    
            if (content.isBlank()) {
                logger.warn("Ollama response content is blank.");
                return "";
            }
            return content;
        } catch (IOException e) {
            logger.error("Failed to parse the Ollama response body: {}", responseBody, e);
            return "";
        }
    }
}