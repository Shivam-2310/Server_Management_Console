package com.management.console.controller;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;

/**
 * Simple test to check actual HTTP responses from running backend.
 * This test connects to the running backend and checks if responses are empty.
 * 
 * To run: Make sure backend is running on port 8080, then run:
 * mvn test -Dtest=EndpointResponseTest
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public class EndpointResponseTest {

    @LocalServerPort
    private int port;

    private WebClient webClient;

    @Test
    void testInfrastructureEndpoint_CheckResponse() {
        // Skip if backend is not running
        if (port == 0) {
            System.out.println("Skipping test - backend not running");
            return;
        }

        webClient = WebClient.builder()
                .baseUrl("http://localhost:" + port)
                .build();

        Long serviceId = 225L;

        System.out.println("=== TESTING INFRASTRUCTURE ENDPOINT ===");
        System.out.println("URL: http://localhost:" + port + "/api/services/" + serviceId + "/infrastructure");

        Mono<String> responseMono = webClient.get()
                .uri("/api/services/{id}/infrastructure", serviceId)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(10));

        StepVerifier.create(responseMono)
                .thenConsumeWhile(response -> {
                    System.out.println("Response Status: OK");
                    System.out.println("Response Length: " + (response != null ? response.length() : 0));
                    System.out.println("Response Body: " + (response != null && response.length() < 500 
                            ? response 
                            : (response != null ? response.substring(0, Math.min(500, response.length())) + "..." : "null")));
                    
                    if (response == null || response.isEmpty() || response.trim().isEmpty()) {
                        System.err.println("❌ ERROR: Response is EMPTY!");
                        return false;
                    } else {
                        System.out.println("✅ SUCCESS: Response contains data");
                        return true;
                    }
                })
                .verifyComplete();
    }

    @Test
    void testConfigurationEndpoint_CheckResponse() {
        // Skip if backend is not running
        if (port == 0) {
            System.out.println("Skipping test - backend not running");
            return;
        }

        webClient = WebClient.builder()
                .baseUrl("http://localhost:" + port)
                .build();

        Long serviceId = 225L;

        System.out.println("=== TESTING CONFIGURATION ENDPOINT ===");
        System.out.println("URL: http://localhost:" + port + "/api/services/" + serviceId + "/configuration");

        Mono<String> responseMono = webClient.get()
                .uri("/api/services/{id}/configuration", serviceId)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(10));

        StepVerifier.create(responseMono)
                .thenConsumeWhile(response -> {
                    System.out.println("Response Status: OK");
                    System.out.println("Response Length: " + (response != null ? response.length() : 0));
                    System.out.println("Response Body: " + (response != null && response.length() < 500 
                            ? response 
                            : (response != null ? response.substring(0, Math.min(500, response.length())) + "..." : "null")));
                    
                    if (response == null || response.isEmpty() || response.trim().isEmpty()) {
                        System.err.println("❌ ERROR: Response is EMPTY!");
                        return false;
                    } else {
                        System.out.println("✅ SUCCESS: Response contains data");
                        return true;
                    }
                })
                .verifyComplete();
    }

    @Test
    void testMetricsEndpoint_ForComparison() {
        // Test metrics endpoint to compare (this one works)
        if (port == 0) {
            System.out.println("Skipping test - backend not running");
            return;
        }

        webClient = WebClient.builder()
                .baseUrl("http://localhost:" + port)
                .build();

        Long serviceId = 225L;

        System.out.println("=== TESTING METRICS ENDPOINT (FOR COMPARISON) ===");
        System.out.println("URL: http://localhost:" + port + "/api/services/" + serviceId + "/metrics?hours=24");

        Mono<String> responseMono = webClient.get()
                .uri("/api/services/{id}/metrics?hours=24", serviceId)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(10));

        StepVerifier.create(responseMono)
                .thenConsumeWhile(response -> {
                    System.out.println("Response Status: OK");
                    System.out.println("Response Length: " + (response != null ? response.length() : 0));
                    System.out.println("Response Body (first 200 chars): " + (response != null && response.length() > 0
                            ? response.substring(0, Math.min(200, response.length())) + "..."
                            : "null"));
                    
                    if (response == null || response.isEmpty()) {
                        System.err.println("❌ ERROR: Metrics response is also empty!");
                        return false;
                    } else {
                        System.out.println("✅ SUCCESS: Metrics response contains data");
                        return true;
                    }
                })
                .verifyComplete();
    }
}

