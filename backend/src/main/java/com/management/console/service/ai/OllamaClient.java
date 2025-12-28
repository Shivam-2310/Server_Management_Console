package com.management.console.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class OllamaClient {

    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;

    @Value("${app.ai.ollama.base-url:http://localhost:11434}")
    private String ollamaBaseUrl;

    @Value("${app.ai.ollama.model:llama3.2:1b}")
    private String modelName;

    @Value("${app.ai.ollama.timeout:30000}")
    private int timeoutMs;

    @Value("${app.ai.ollama.enabled:true}")
    private boolean enabled;

    public String generateResponse(String prompt) {
        if (!enabled) {
            log.debug("Ollama AI is disabled");
            return null;
        }

        try {
            WebClient webClient = webClientBuilder
                    .baseUrl(ollamaBaseUrl)
                    .build();

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", modelName);
            requestBody.put("prompt", prompt);
            requestBody.put("stream", false);
            requestBody.put("options", Map.of(
                    "temperature", 0.7,
                    "top_p", 0.9,
                    "num_predict", 500
            ));

            String response = webClient.post()
                    .uri("/api/generate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofMillis(timeoutMs))
                    .onErrorResume(e -> {
                        log.error("Ollama request failed: {}", e.getMessage());
                        return Mono.empty();
                    })
                    .block();

            if (response != null) {
                JsonNode jsonNode = objectMapper.readTree(response);
                return jsonNode.path("response").asText();
            }

        } catch (Exception e) {
            log.error("Failed to get AI response: {}", e.getMessage());
        }

        return null;
    }

    public String generateChatResponse(String systemPrompt, String userMessage) {
        if (!enabled) {
            log.debug("Ollama AI is disabled");
            return null;
        }

        try {
            WebClient webClient = webClientBuilder
                    .baseUrl(ollamaBaseUrl)
                    .build();

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", modelName);
            requestBody.put("messages", new Object[]{
                    Map.of("role", "system", "content", systemPrompt),
                    Map.of("role", "user", "content", userMessage)
            });
            requestBody.put("stream", false);
            requestBody.put("options", Map.of(
                    "temperature", 0.7,
                    "top_p", 0.9,
                    "num_predict", 500
            ));

            String response = webClient.post()
                    .uri("/api/chat")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofMillis(timeoutMs))
                    .onErrorResume(e -> {
                        log.error("Ollama chat request failed: {}", e.getMessage());
                        return Mono.empty();
                    })
                    .block();

            if (response != null) {
                JsonNode jsonNode = objectMapper.readTree(response);
                return jsonNode.path("message").path("content").asText();
            }

        } catch (Exception e) {
            log.error("Failed to get AI chat response: {}", e.getMessage());
        }

        return null;
    }

    public boolean isAvailable() {
        if (!enabled) {
            return false;
        }

        try {
            WebClient webClient = webClientBuilder
                    .baseUrl(ollamaBaseUrl)
                    .build();

            String response = webClient.get()
                    .uri("/api/tags")
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(5))
                    .block();

            return response != null;
        } catch (Exception e) {
            log.warn("Ollama is not available: {}", e.getMessage());
            return false;
        }
    }

    public boolean isModelLoaded() {
        if (!enabled) {
            return false;
        }

        try {
            WebClient webClient = webClientBuilder
                    .baseUrl(ollamaBaseUrl)
                    .build();

            String response = webClient.get()
                    .uri("/api/tags")
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(5))
                    .block();

            if (response != null) {
                JsonNode jsonNode = objectMapper.readTree(response);
                JsonNode models = jsonNode.path("models");
                if (models.isArray()) {
                    for (JsonNode model : models) {
                        if (model.path("name").asText().contains(modelName.split(":")[0])) {
                            return true;
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to check model: {}", e.getMessage());
        }

        return false;
    }
}

