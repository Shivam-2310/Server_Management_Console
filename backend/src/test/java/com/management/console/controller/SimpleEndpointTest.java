package com.management.console.controller;

import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

/**
 * Simple standalone test to check endpoint responses.
 * This test directly calls the running backend (port 8080) and prints the responses.
 * 
 * Run with: mvn test -Dtest=SimpleEndpointTest
 * Make sure backend is running first!
 */
public class SimpleEndpointTest {

    private static final String BASE_URL = "http://localhost:8080";
    private static final Long SERVICE_ID = 225L;

    @Test
    void testInfrastructureEndpoint() {
        System.out.println("\n========================================");
        System.out.println("TESTING INFRASTRUCTURE ENDPOINT");
        System.out.println("========================================");
        System.out.println("URL: " + BASE_URL + "/api/services/" + SERVICE_ID + "/infrastructure\n");

        WebClient webClient = WebClient.builder()
                .baseUrl(BASE_URL)
                .build();

        try {
            String response = webClient.get()
                    .uri("/api/services/{id}/infrastructure", SERVICE_ID)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();

            System.out.println("Response received!");
            System.out.println("Response length: " + (response != null ? response.length() : 0));
            System.out.println("\nResponse body:");
            System.out.println("----------------------------------------");
            
            if (response == null || response.isEmpty() || response.trim().isEmpty()) {
                System.err.println("❌ ERROR: Response is EMPTY or NULL!");
                System.err.println("This indicates a serialization or response handling issue.");
            } else {
                System.out.println(response.length() > 1000 
                        ? response.substring(0, 1000) + "\n... (truncated)" 
                        : response);
                System.out.println("\n✅ SUCCESS: Response contains data");
            }
            
            System.out.println("----------------------------------------\n");
        } catch (Exception e) {
            System.err.println("❌ ERROR: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Test
    void testConfigurationEndpoint() {
        System.out.println("\n========================================");
        System.out.println("TESTING CONFIGURATION ENDPOINT");
        System.out.println("========================================");
        System.out.println("URL: " + BASE_URL + "/api/services/" + SERVICE_ID + "/configuration\n");

        WebClient webClient = WebClient.builder()
                .baseUrl(BASE_URL)
                .build();

        try {
            String response = webClient.get()
                    .uri("/api/services/{id}/configuration", SERVICE_ID)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();

            System.out.println("Response received!");
            System.out.println("Response length: " + (response != null ? response.length() : 0));
            System.out.println("\nResponse body:");
            System.out.println("----------------------------------------");
            
            if (response == null || response.isEmpty() || response.trim().isEmpty()) {
                System.err.println("❌ ERROR: Response is EMPTY or NULL!");
                System.err.println("This indicates a serialization or response handling issue.");
            } else {
                System.out.println(response.length() > 1000 
                        ? response.substring(0, 1000) + "\n... (truncated)" 
                        : response);
                System.out.println("\n✅ SUCCESS: Response contains data");
            }
            
            System.out.println("----------------------------------------\n");
        } catch (Exception e) {
            System.err.println("❌ ERROR: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Test
    void testMetricsEndpoint_ForComparison() {
        System.out.println("\n========================================");
        System.out.println("TESTING METRICS ENDPOINT (FOR COMPARISON)");
        System.out.println("========================================");
        System.out.println("URL: " + BASE_URL + "/api/services/" + SERVICE_ID + "/metrics?hours=24\n");

        WebClient webClient = WebClient.builder()
                .baseUrl(BASE_URL)
                .build();

        try {
            String response = webClient.get()
                    .uri("/api/services/{id}/metrics?hours=24", SERVICE_ID)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();

            System.out.println("Response received!");
            System.out.println("Response length: " + (response != null ? response.length() : 0));
            System.out.println("\nResponse body (first 500 chars):");
            System.out.println("----------------------------------------");
            
            if (response == null || response.isEmpty()) {
                System.err.println("❌ ERROR: Metrics response is also empty!");
            } else {
                System.out.println(response.substring(0, Math.min(500, response.length())));
                System.out.println("\n✅ SUCCESS: Metrics response contains data (this one works)");
            }
            
            System.out.println("----------------------------------------\n");
        } catch (Exception e) {
            System.err.println("❌ ERROR: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

