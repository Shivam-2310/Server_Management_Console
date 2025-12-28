package com.management.console.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.management.console.domain.entity.HealthCheckResult;
import com.management.console.domain.entity.ManagedService;
import com.management.console.domain.enums.HealthStatus;
import com.management.console.domain.enums.ServiceType;
import com.management.console.dto.HealthCheckDTO;
import com.management.console.mapper.ServiceMapper;
import com.management.console.repository.HealthCheckResultRepository;
import com.management.console.repository.ManagedServiceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class HealthMonitorService {

    private final ManagedServiceRepository serviceRepository;
    private final HealthCheckResultRepository healthCheckRepository;
    private final ServiceMapper serviceMapper;
    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;

    @Transactional
    public HealthCheckDTO performHealthCheck(Long serviceId) {
        ManagedService service = serviceRepository.findById(serviceId)
                .orElseThrow(() -> new RuntimeException("Service not found: " + serviceId));
        
        return performHealthCheck(service);
    }

    @Transactional
    public HealthCheckDTO performHealthCheck(ManagedService service) {
        log.debug("Performing health check for service: {}", service.getName());
        
        long startTime = System.currentTimeMillis();
        HealthCheckResult result = HealthCheckResult.builder()
                .service(service)
                .timestamp(LocalDateTime.now())
                .build();

        try {
            if (service.getServiceType() == ServiceType.BACKEND) {
                performBackendHealthCheck(service, result);
            } else {
                performFrontendHealthCheck(service, result);
            }
        } catch (Exception e) {
            log.error("Health check failed for service {}: {}", service.getName(), e.getMessage());
            result.setStatus(HealthStatus.DOWN);
            result.setErrorMessage(e.getMessage());
            result.setErrorType(e.getClass().getSimpleName());
        }

        result.setResponseTimeMs(System.currentTimeMillis() - startTime);
        
        // Save result
        HealthCheckResult saved = healthCheckRepository.save(result);
        
        // Update service status
        updateServiceHealth(service, result.getStatus());
        
        return serviceMapper.toDTO(saved);
    }

    private void performBackendHealthCheck(ManagedService service, HealthCheckResult result) {
        String healthUrl = service.getActuatorUrl() + "/health";
        
        try {
            WebClient webClient = webClientBuilder.build();
            
            ResponseEntity<String> response = webClient.get()
                    .uri(healthUrl)
                    .retrieve()
                    .toEntity(String.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();

            if (response != null && response.getStatusCode().is2xxSuccessful()) {
                result.setHttpStatusCode(response.getStatusCode().value());
                result.setCheckType("ACTUATOR");
                
                String body = response.getBody();
                if (body != null) {
                    JsonNode healthJson = objectMapper.readTree(body);
                    String status = healthJson.path("status").asText("UNKNOWN");
                    
                    result.setStatus(mapActuatorStatus(status));
                    result.setStatusMessage(status);
                    
                    // Store component details
                    JsonNode components = healthJson.path("components");
                    if (!components.isMissingNode()) {
                        result.setComponentDetails(components.toString());
                        
                        // Extract database health if present
                        JsonNode db = components.path("db");
                        if (!db.isMissingNode()) {
                            String dbStatus = db.path("status").asText();
                            result.setDatabaseHealthy("UP".equalsIgnoreCase(dbStatus));
                            result.setDatabaseStatus(dbStatus);
                        }
                        
                        // Extract disk space if present
                        JsonNode diskSpace = components.path("diskSpace");
                        if (!diskSpace.isMissingNode()) {
                            JsonNode details = diskSpace.path("details");
                            result.setDiskSpaceFree(details.path("free").asLong(0));
                            result.setDiskSpaceTotal(details.path("total").asLong(0));
                        }
                    }
                }
            } else {
                result.setStatus(HealthStatus.DOWN);
                result.setHttpStatusCode(response != null ? response.getStatusCode().value() : 0);
            }
        } catch (Exception e) {
            log.warn("Actuator health check failed for {}, trying HTTP ping", service.getName());
            performSimpleHttpCheck(service, result);
        }
    }

    private void performFrontendHealthCheck(ManagedService service, HealthCheckResult result) {
        result.setCheckType("HTTP");
        
        String healthUrl = service.getHealthEndpoint() != null 
                ? service.getFullUrl() + service.getHealthEndpoint()
                : service.getFullUrl();

        try {
            WebClient webClient = webClientBuilder.build();
            
            ResponseEntity<String> response = webClient.get()
                    .uri(healthUrl)
                    .retrieve()
                    .toEntity(String.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();

            if (response != null) {
                result.setHttpStatusCode(response.getStatusCode().value());
                
                if (response.getStatusCode().is2xxSuccessful()) {
                    result.setStatus(HealthStatus.HEALTHY);
                    result.setStatusMessage("Frontend responding normally");
                } else if (response.getStatusCode().is5xxServerError()) {
                    result.setStatus(HealthStatus.DOWN);
                    result.setStatusMessage("Server error");
                } else {
                    result.setStatus(HealthStatus.DEGRADED);
                    result.setStatusMessage("Non-2xx response");
                }
            } else {
                result.setStatus(HealthStatus.DOWN);
            }
        } catch (Exception e) {
            result.setStatus(HealthStatus.DOWN);
            result.setErrorMessage(e.getMessage());
        }
    }

    private void performSimpleHttpCheck(ManagedService service, HealthCheckResult result) {
        result.setCheckType("PING");
        
        try {
            WebClient webClient = webClientBuilder.build();
            
            ResponseEntity<String> response = webClient.get()
                    .uri(service.getFullUrl())
                    .retrieve()
                    .toEntity(String.class)
                    .timeout(Duration.ofSeconds(5))
                    .block();

            if (response != null && response.getStatusCode().is2xxSuccessful()) {
                result.setStatus(HealthStatus.HEALTHY);
                result.setHttpStatusCode(response.getStatusCode().value());
            } else {
                result.setStatus(HealthStatus.DOWN);
                result.setHttpStatusCode(response != null ? response.getStatusCode().value() : 0);
            }
        } catch (Exception e) {
            result.setStatus(HealthStatus.DOWN);
            result.setErrorMessage("Service unreachable: " + e.getMessage());
        }
    }

    private HealthStatus mapActuatorStatus(String actuatorStatus) {
        return switch (actuatorStatus.toUpperCase()) {
            case "UP" -> HealthStatus.HEALTHY;
            case "DOWN" -> HealthStatus.DOWN;
            case "OUT_OF_SERVICE" -> HealthStatus.DOWN;
            case "UNKNOWN" -> HealthStatus.UNKNOWN;
            default -> HealthStatus.DEGRADED;
        };
    }

    @Transactional
    public void updateServiceHealth(ManagedService service, HealthStatus status) {
        service.setHealthStatus(status);
        service.setLastHealthCheck(LocalDateTime.now());
        service.setIsRunning(status != HealthStatus.DOWN);
        serviceRepository.save(service);
    }

    @Async
    public CompletableFuture<List<HealthCheckDTO>> performHealthCheckForAll() {
        log.info("Performing health check for all enabled services");
        
        List<ManagedService> services = serviceRepository.findByEnabledTrue();
        
        List<HealthCheckDTO> results = services.stream()
                .map(this::performHealthCheck)
                .collect(Collectors.toList());
        
        return CompletableFuture.completedFuture(results);
    }

    @Transactional(readOnly = true)
    public List<HealthCheckDTO> getRecentHealthChecks(Long serviceId, int hours) {
        LocalDateTime since = LocalDateTime.now().minusHours(hours);
        return healthCheckRepository.findRecentChecks(serviceId, since).stream()
                .map(serviceMapper::toDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public HealthCheckDTO getLatestHealthCheck(Long serviceId) {
        return healthCheckRepository.findTopByServiceIdOrderByTimestampDesc(serviceId)
                .map(serviceMapper::toDTO)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public Double getAverageResponseTime(Long serviceId, int hours) {
        LocalDateTime since = LocalDateTime.now().minusHours(hours);
        return healthCheckRepository.getAverageResponseTime(serviceId, since);
    }

    /**
     * Derive health status from multiple signals (not just one endpoint)
     */
    public HealthStatus deriveHealthStatus(Long serviceId, HealthCheckResult latestCheck, 
                                           Double errorRate, Double cpuUsage, Double memoryUsage) {
        // If health check says DOWN, it's DOWN
        if (latestCheck != null && latestCheck.getStatus() == HealthStatus.DOWN) {
            return HealthStatus.DOWN;
        }

        // Check for CRITICAL conditions
        if (errorRate != null && errorRate > 10.0) {
            return HealthStatus.CRITICAL;
        }
        if (cpuUsage != null && cpuUsage > 95.0) {
            return HealthStatus.CRITICAL;
        }
        if (memoryUsage != null && memoryUsage > 95.0) {
            return HealthStatus.CRITICAL;
        }

        // Check for DEGRADED conditions
        if (errorRate != null && errorRate > 5.0) {
            return HealthStatus.DEGRADED;
        }
        if (cpuUsage != null && cpuUsage > 80.0) {
            return HealthStatus.DEGRADED;
        }
        if (memoryUsage != null && memoryUsage > 85.0) {
            return HealthStatus.DEGRADED;
        }
        if (latestCheck != null && latestCheck.getResponseTimeMs() != null && 
            latestCheck.getResponseTimeMs() > 5000) {
            return HealthStatus.DEGRADED;
        }

        // Otherwise, use health check result or HEALTHY
        if (latestCheck != null) {
            return latestCheck.getStatus();
        }
        
        return HealthStatus.UNKNOWN;
    }
}

