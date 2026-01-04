package com.management.console.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class GeminiClient {

    private final ObjectMapper objectMapper;
    private WebClient webClient;

    @Value("${app.ai.gemini.api-key:AIzaSyCId4zpV3OlqMY1MqNtjeRtYgE12TSzdn0}")
    private String apiKey;

    @Value("${app.ai.gemini.model:gemini-1.5-flash}")
    private String modelName;

    @Value("${app.ai.gemini.timeout:120000}")
    private int timeoutMs;

    @Value("${app.ai.gemini.enabled:true}")
    private boolean enabled;

    private static final String GEMINI_API_BASE_URL = "https://generativelanguage.googleapis.com/v1beta";

    public GeminiClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Get or create a WebClient configured with appropriate timeout for Gemini API.
     * The timeout is configurable via app.ai.gemini.timeout (default: 120 seconds).
     */
    private WebClient getWebClient() {
        if (webClient == null) {
            // Create a dedicated HttpClient with longer timeout for Gemini
            // Convert timeout from milliseconds to seconds for responseTimeout
            int timeoutSeconds = (int) Math.ceil(timeoutMs / 1000.0);
            // Add some buffer for connection time
            int responseTimeoutSeconds = Math.max(timeoutSeconds + 10, 120);
            
            HttpClient httpClient = HttpClient.create()
                    .responseTimeout(Duration.ofSeconds(responseTimeoutSeconds))
                    .followRedirect(true);

            webClient = WebClient.builder()
                    .baseUrl(GEMINI_API_BASE_URL)
                    .clientConnector(new ReactorClientHttpConnector(httpClient))
                    .build();
            
            log.info("Gemini WebClient configured with {}ms timeout ({}s response timeout)", 
                    timeoutMs, responseTimeoutSeconds);
        }
        return webClient;
    }

    public String generateResponse(String prompt) {
        if (!enabled) {
            log.debug("Gemini AI is disabled");
            return null;
        }

        try {
            WebClient webClient = getWebClient();

            // Build Gemini API request format
            List<Map<String, Object>> contents = new ArrayList<>();
            Map<String, Object> userMessage = new HashMap<>();
            userMessage.put("role", "user");
            
            List<Map<String, Object>> messageParts = new ArrayList<>();
            Map<String, Object> textPart = new HashMap<>();
            textPart.put("text", prompt);
            messageParts.add(textPart);
            userMessage.put("parts", messageParts);
            contents.add(userMessage);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("contents", contents);
            
            // Generation config
            Map<String, Object> generationConfig = new HashMap<>();
            generationConfig.put("temperature", 0.7);
            generationConfig.put("topP", 0.9);
            generationConfig.put("maxOutputTokens", 500);
            requestBody.put("generationConfig", generationConfig);

            String uri = String.format("/models/%s:generateContent?key=%s", modelName, apiKey);

            String response = webClient.post()
                    .uri(uri)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofMillis(timeoutMs))
                    .onErrorResume(e -> {
                        log.error("Gemini request failed: {}", e.getMessage());
                        return Mono.empty();
                    })
                    .block();

            if (response != null) {
                JsonNode jsonNode = objectMapper.readTree(response);
                
                // Check for errors first
                if (jsonNode.has("error")) {
                    JsonNode error = jsonNode.path("error");
                    log.error("Gemini API error: {} - {}", 
                        error.path("code").asText("UNKNOWN"),
                        error.path("message").asText("Unknown error"));
                    return null;
                }
                
                JsonNode candidates = jsonNode.path("candidates");
                if (candidates.isArray() && candidates.size() > 0) {
                    JsonNode firstCandidate = candidates.get(0);
                    
                    // Check for finish reason (safety filters, etc.)
                    if (firstCandidate.has("finishReason")) {
                        String finishReason = firstCandidate.path("finishReason").asText();
                        if (!"STOP".equals(finishReason)) {
                            log.warn("Gemini response finished with reason: {}", finishReason);
                        }
                    }
                    
                    JsonNode content = firstCandidate.path("content");
                    JsonNode responseParts = content.path("parts");
                    if (responseParts.isArray() && responseParts.size() > 0) {
                        return responseParts.get(0).path("text").asText();
                    }
                }
            }

        } catch (Exception e) {
            log.error("Failed to get AI response from Gemini: {}", e.getMessage(), e);
        }

        return null;
    }

    public String generateChatResponse(String systemPrompt, String userMessage) {
        if (!enabled) {
            log.debug("Gemini AI is disabled");
            return null;
        }

        try {
            WebClient webClient = getWebClient();

            // Build Gemini API request format with system instruction and user message
            List<Map<String, Object>> contents = new ArrayList<>();
            
            // System instruction (Gemini uses systemInstruction in the request)
            Map<String, Object> systemInstruction = new HashMap<>();
            List<Map<String, Object>> systemParts = new ArrayList<>();
            Map<String, Object> systemTextPart = new HashMap<>();
            systemTextPart.put("text", systemPrompt);
            systemParts.add(systemTextPart);
            systemInstruction.put("parts", systemParts);
            
            // User message
            Map<String, Object> userMsg = new HashMap<>();
            userMsg.put("role", "user");
            List<Map<String, Object>> userParts = new ArrayList<>();
            Map<String, Object> userTextPart = new HashMap<>();
            userTextPart.put("text", userMessage);
            userParts.add(userTextPart);
            userMsg.put("parts", userParts);
            contents.add(userMsg);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("contents", contents);
            requestBody.put("systemInstruction", systemInstruction);
            
            // Generation config
            Map<String, Object> generationConfig = new HashMap<>();
            generationConfig.put("temperature", 0.7);
            generationConfig.put("topP", 0.9);
            generationConfig.put("maxOutputTokens", 500);
            requestBody.put("generationConfig", generationConfig);

            String uri = String.format("/models/%s:generateContent?key=%s", modelName, apiKey);

            String response = webClient.post()
                    .uri(uri)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofMillis(timeoutMs))
                    .onErrorResume(e -> {
                        log.error("Gemini chat request failed: {}", e.getMessage());
                        return Mono.empty();
                    })
                    .block();

            if (response != null) {
                JsonNode jsonNode = objectMapper.readTree(response);
                
                // Check for errors first
                if (jsonNode.has("error")) {
                    JsonNode error = jsonNode.path("error");
                    log.error("Gemini API error: {} - {}", 
                        error.path("code").asText("UNKNOWN"),
                        error.path("message").asText("Unknown error"));
                    return null;
                }
                
                JsonNode candidates = jsonNode.path("candidates");
                if (candidates.isArray() && candidates.size() > 0) {
                    JsonNode firstCandidate = candidates.get(0);
                    
                    // Check for finish reason (safety filters, etc.)
                    if (firstCandidate.has("finishReason")) {
                        String finishReason = firstCandidate.path("finishReason").asText();
                        if (!"STOP".equals(finishReason)) {
                            log.warn("Gemini response finished with reason: {}", finishReason);
                        }
                    }
                    
                    JsonNode content = firstCandidate.path("content");
                    JsonNode responseParts = content.path("parts");
                    if (responseParts.isArray() && responseParts.size() > 0) {
                        return responseParts.get(0).path("text").asText();
                    }
                }
            }

        } catch (Exception e) {
            log.error("Failed to get AI chat response from Gemini: {}", e.getMessage());
        }

        return null;
    }

    public boolean isAvailable() {
        if (!enabled) {
            return false;
        }

        try {
            // Test availability by making a simple request
            WebClient webClient = getWebClient();
            String testPrompt = "test";
            
            List<Map<String, Object>> contents = new ArrayList<>();
            Map<String, Object> userMessage = new HashMap<>();
            userMessage.put("role", "user");
            List<Map<String, Object>> parts = new ArrayList<>();
            Map<String, Object> textPart = new HashMap<>();
            textPart.put("text", testPrompt);
            parts.add(textPart);
            userMessage.put("parts", parts);
            contents.add(userMessage);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("contents", contents);

            String uri = String.format("/models/%s:generateContent?key=%s", modelName, apiKey);

            String response = webClient.post()
                    .uri(uri)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();

            if (response != null) {
                JsonNode jsonNode = objectMapper.readTree(response);
                // Check for errors
                if (jsonNode.has("error")) {
                    log.warn("Gemini API error during availability check: {}", 
                        jsonNode.path("error").path("message").asText("Unknown error"));
                    return false;
                }
                // If we got a response without errors, API is available
                return true;
            }
            return false;
        } catch (Exception e) {
            log.warn("Gemini is not available: {}", e.getMessage());
            return false;
        }
    }

    public boolean isModelLoaded() {
        // For Gemini API, models are always available via API
        // This method is kept for compatibility but always returns true if enabled
        return enabled && isAvailable();
    }
}

