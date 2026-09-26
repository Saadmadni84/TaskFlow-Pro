package com.taskflow.taskflow.ai.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskflow.taskflow.ai.config.AiProperties;
import com.taskflow.taskflow.ai.exception.AiMalformedOutputException;
import com.taskflow.taskflow.ai.exception.AiProviderException;
import com.taskflow.taskflow.ai.model.DependencySuggestionContext;
import com.taskflow.taskflow.ai.model.DependencySuggestionResult;
import com.taskflow.taskflow.ai.model.RawDependencySuggestion;
import com.taskflow.taskflow.ai.prompt.DependencySuggestionPromptBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * Concrete LLM provider implementation communicating with Google Gemini via REST API.
 * Uses native Java 21 HttpClient with strict timeouts and structured JSON parsing.
 */
@Component
@ConditionalOnProperty(name = "ai.provider", havingValue = "gemini", matchIfMissing = true)
public class GeminiDependencySuggestionProvider implements DependencySuggestionProvider {

    private static final Logger log = LoggerFactory.getLogger(GeminiDependencySuggestionProvider.class);
    private static final String GEMINI_API_BASE = "https://generativelanguage.googleapis.com/v1beta/models";

    private final AiProperties aiProperties;
    private final DependencySuggestionPromptBuilder promptBuilder;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public GeminiDependencySuggestionProvider(
            AiProperties aiProperties,
            DependencySuggestionPromptBuilder promptBuilder,
            ObjectMapper objectMapper
    ) {
        this.aiProperties = aiProperties;
        this.promptBuilder = promptBuilder;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(Math.max(1, aiProperties.getTimeoutSeconds())))
                .build();
    }

    @Override
    public DependencySuggestionResult suggest(DependencySuggestionContext context) {
        if (!aiProperties.isEnabled()) {
            return DependencySuggestionResult.empty();
        }

        String apiKey = aiProperties.getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("AI is enabled but no API key configured. Returning empty suggestions.");
            return DependencySuggestionResult.empty();
        }

        String model = aiProperties.getModel() != null && !aiProperties.getModel().isBlank()
                ? aiProperties.getModel()
                : "gemini-1.5-flash";

        String prompt = promptBuilder.buildPrompt(context);
        String endpoint = String.format("%s/%s:generateContent?key=%s", GEMINI_API_BASE, model, apiKey);

        // Build Gemini request body with JSON response enforcement
        String requestJson;
        try {
            var bodyMap = new java.util.LinkedHashMap<String, Object>();
            var contents = List.of(
                    java.util.Map.of("parts", List.of(java.util.Map.of("text", prompt)))
            );
            bodyMap.put("contents", contents);
            bodyMap.put("generationConfig", java.util.Map.of("responseMimeType", "application/json"));
            requestJson = objectMapper.writeValueAsString(bodyMap);
        } catch (Exception e) {
            throw new AiProviderException("Failed to serialize AI request body: " + e.getMessage(), e);
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(Math.max(1, aiProperties.getTimeoutSeconds())))
                .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                .build();

        try {
            log.debug("Dispatching AI dependency suggestion request to model [{}]", model);
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            int status = response.statusCode();
            if (status == 429) {
                log.warn("AI provider rate limited (HTTP 429)");
                throw new AiProviderException("AI suggestion provider rate limit exceeded. Please try again later.");
            } else if (status >= 500) {
                log.warn("AI provider internal error (HTTP {})", status);
                throw new AiProviderException("AI suggestion provider is temporarily unavailable (HTTP " + status + ").");
            } else if (status != 200) {
                log.warn("AI provider error (HTTP {})", status);
                throw new AiProviderException("AI suggestion provider returned unexpected status: " + status);
            }

            return parseGeminiResponse(response.body());

        } catch (HttpTimeoutException e) {
            log.warn("AI provider request timed out after {} seconds", aiProperties.getTimeoutSeconds());
            throw new AiProviderException("AI suggestion request timed out after " + aiProperties.getTimeoutSeconds() + " seconds", e);
        } catch (IOException e) {
            log.warn("Network error communicating with AI provider: {}", e.getMessage());
            throw new AiProviderException("Network error contacting AI provider: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AiProviderException("AI suggestion request was interrupted", e);
        }
    }

    private DependencySuggestionResult parseGeminiResponse(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode textNode = root.path("candidates")
                    .path(0)
                    .path("content")
                    .path("parts")
                    .path(0)
                    .path("text");

            if (textNode.isMissingNode() || textNode.asText().isBlank()) {
                log.debug("Empty candidates received from Gemini provider");
                return DependencySuggestionResult.empty();
            }

            String rawJson = textNode.asText().trim();
            if (rawJson.startsWith("```json")) {
                rawJson = rawJson.substring(7);
            } else if (rawJson.startsWith("```")) {
                rawJson = rawJson.substring(3);
            }
            if (rawJson.endsWith("```")) {
                rawJson = rawJson.substring(0, rawJson.length() - 3);
            }
            rawJson = rawJson.trim();

            JsonNode parsedOutput = objectMapper.readTree(rawJson);
            JsonNode suggestionsArray = parsedOutput.path("suggestions");

            if (!suggestionsArray.isArray()) {
                return DependencySuggestionResult.empty();
            }

            List<RawDependencySuggestion> results = new ArrayList<>();
            for (JsonNode item : suggestionsArray) {
                String predStr = item.path("predecessorTaskId").asText(null);
                String succStr = item.path("successorTaskId").asText(null);
                Double confidence = item.has("confidence") ? item.path("confidence").asDouble() : null;
                String reason = item.path("reason").asText(null);

                UUID predId = parseUuidSilently(predStr);
                UUID succId = parseUuidSilently(succStr);

                results.add(new RawDependencySuggestion(predId, succId, confidence, reason));
            }

            return new DependencySuggestionResult(results);

        } catch (Exception e) {
            log.warn("Malformed output received from AI provider: {}", e.getMessage());
            throw new AiMalformedOutputException("Failed to parse structured output from AI provider: " + e.getMessage(), e);
        }
    }

    private UUID parseUuidSilently(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Override
    public String getProviderName() {
        return "gemini";
    }
}
