package com.management.console.service;

import com.management.console.domain.entity.ManagedService;
import com.management.console.exception.ResourceNotFoundException;
import com.management.console.repository.ManagedServiceRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Diagnostic service to test actuator endpoint connectivity and response format
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ActuatorDiagnosticService {

    private final ManagedServiceRepository serviceRepository;
    private final WebClient.Builder webClientBuilder;

    public Mono<DiagnosticResult> testActuatorEndpoint(Long serviceId, String endpoint) {
        ManagedService service = getService(serviceId);
        String actuatorUrl = buildActuatorUrl(service, endpoint);
        
        DiagnosticResult result = new DiagnosticResult();
        result.setServiceId(serviceId);
        result.setServiceName(service.getName());
        result.setEndpoint(endpoint);
        result.setActuatorUrl(actuatorUrl);
        
        // Set explicit buffer limit for diagnostic responses
        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(-1)) // Unlimited - maximum memory
                .build();

        WebClient webClient = webClientBuilder
                .baseUrl("")
                .exchangeStrategies(strategies) // Explicitly set buffer limit
                .build();
        
        log.info("=== DIAGNOSTIC TEST ===");
        log.info("Service: {} (ID: {})", service.getName(), serviceId);
        log.info("Endpoint: {}", endpoint);
        log.info("Full URL: {}", actuatorUrl);
        log.info("Host: {}, Port: {}", service.getHost(), service.getPort());
        log.info("Actuator Base Path: {}", service.getActuatorBasePath());
        
        return webClient.get()
                .uri(actuatorUrl)
                .exchangeToMono(response -> {
                    result.setStatusCode(response.statusCode().value());
                    result.setStatusText(response.statusCode().toString());
                    
                    log.info("Response Status: {} {}", result.getStatusCode(), result.getStatusText());
                    
                    if (response.statusCode().is2xxSuccessful()) {
                        // Try to read as string first
                        return response.bodyToMono(String.class)
                                .timeout(Duration.ofSeconds(10))
                                .map(body -> {
                                    result.setSuccess(true);
                                    result.setResponseLength(body != null ? body.length() : 0);
                                    result.setResponsePreview(body != null && body.length() > 0 
                                            ? body.substring(0, Math.min(500, body.length())) 
                                            : "");
                                    result.setResponseType("text");
                                    
                                    log.info("Response received: {} characters", result.getResponseLength());
                                    log.debug("Response preview (first 500 chars): {}", result.getResponsePreview());
                                    
                                    return result;
                                })
                                .onErrorResume(e -> {
                                    log.error("Error reading response body: {}", e.getMessage(), e);
                                    result.setSuccess(false);
                                    result.setError(e.getMessage());
                                    return Mono.just(result);
                                });
                    } else {
                        result.setSuccess(false);
                        result.setError("HTTP " + result.getStatusCode() + " " + result.getStatusText());
                        log.error("Non-2xx response: {} {}", result.getStatusCode(), result.getStatusText());
                        return Mono.just(result);
                    }
                })
                .onErrorResume(e -> {
                    log.error("Request failed: {}", e.getMessage(), e);
                    result.setSuccess(false);
                    result.setError(e.getClass().getSimpleName() + ": " + e.getMessage());
                    return Mono.just(result);
                });
    }

    private ManagedService getService(Long serviceId) {
        return serviceRepository.findById(serviceId)
                .orElseThrow(() -> new ResourceNotFoundException("Service not found: " + serviceId));
    }

    private String buildActuatorUrl(ManagedService service, String endpoint) {
        String baseUrl = service.getActuatorUrl();
        if (baseUrl == null || baseUrl.isEmpty()) {
            baseUrl = String.format("http://%s:%d/actuator",
                    service.getHost() != null ? service.getHost() : "localhost",
                    service.getPort() != null ? service.getPort() : 8080);
        }
        return baseUrl + endpoint;
    }

    @Data
    public static class DiagnosticResult {
        private Long serviceId;
        private String serviceName;
        private String endpoint;
        private String actuatorUrl;
        private boolean success;
        private int statusCode;
        private String statusText;
        private int responseLength;
        private String responsePreview;
        private String responseType;
        private String error;
    }
}

