package com.management.console.controller;

import com.management.console.domain.enums.HealthStatus;
import com.management.console.domain.enums.ServiceType;
import com.management.console.dto.*;
import com.management.console.exception.DuplicateResourceException;
import com.management.console.service.HealthMonitorService;
import com.management.console.service.MetricsCollectorService;
import com.management.console.service.ServiceRegistryService;
import com.management.console.service.ai.AIIntelligenceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/services")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class ServiceController {

    private final ServiceRegistryService serviceRegistryService;
    private final HealthMonitorService healthMonitorService;
    private final MetricsCollectorService metricsCollectorService;
    private final AIIntelligenceService aiService;

    @PostMapping
    public ResponseEntity<ServiceDTO> registerService(@Valid @RequestBody CreateServiceRequest request) {
        try {
            log.info("=== Service Registration Request Received ===");
            log.info("Service Name: {}", request.getName());
            log.info("Service Type: {}", request.getServiceType());
            log.info("Host: {}", request.getHost());
            log.info("Port: {}", request.getPort());
            log.debug("Full request: {}", request);
            
            if (request == null) {
                log.error("Request body is null");
                throw new IllegalArgumentException("Request body cannot be null");
            }
            
            ServiceDTO service = serviceRegistryService.registerService(request);
            log.info("Service registered successfully with ID: {}", service.getId());
            return ResponseEntity.status(HttpStatus.CREATED).body(service);
        } catch (DuplicateResourceException e) {
            log.error("Duplicate service registration attempt: {}", e.getMessage());
            throw e;
        } catch (IllegalArgumentException e) {
            log.error("Invalid request: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Error registering service: {}", e.getMessage(), e);
            log.error("Exception type: {}", e.getClass().getName());
            if (e.getCause() != null) {
                log.error("Caused by: {}", e.getCause().getMessage());
            }
            throw new RuntimeException("Failed to register service: " + e.getMessage(), e);
        }
    }

    @GetMapping
    public ResponseEntity<List<ServiceDTO>> getAllServices() {
        return ResponseEntity.ok(serviceRegistryService.getAllServices());
    }

    @GetMapping("/enabled")
    public ResponseEntity<List<ServiceDTO>> getEnabledServices() {
        return ResponseEntity.ok(serviceRegistryService.getEnabledServices());
    }

    @GetMapping("/type/{type}")
    public ResponseEntity<List<ServiceDTO>> getServicesByType(@PathVariable ServiceType type) {
        return ResponseEntity.ok(serviceRegistryService.getServicesByType(type));
    }

    @GetMapping("/health/{status}")
    public ResponseEntity<List<ServiceDTO>> getServicesByHealth(@PathVariable HealthStatus status) {
        return ResponseEntity.ok(serviceRegistryService.getServicesByHealth(status));
    }

    @GetMapping("/high-risk")
    public ResponseEntity<List<ServiceDTO>> getHighRiskServices(
            @RequestParam(defaultValue = "70") int threshold) {
        return ResponseEntity.ok(serviceRegistryService.getHighRiskServices(threshold));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ServiceDTO> getService(@PathVariable Long id) {
        return ResponseEntity.ok(serviceRegistryService.getService(id));
    }

    @GetMapping("/name/{name}")
    public ResponseEntity<ServiceDTO> getServiceByName(@PathVariable String name) {
        return ResponseEntity.ok(serviceRegistryService.getServiceByName(name));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ServiceDTO> updateService(
            @PathVariable Long id,
            @RequestBody UpdateServiceRequest request) {
        log.info("Updating service: {}", id);
        return ResponseEntity.ok(serviceRegistryService.updateService(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteService(@PathVariable Long id) {
        log.info("Deleting service: {}", id);
        serviceRegistryService.deleteService(id);
        return ResponseEntity.noContent().build();
    }

    // Health Check Endpoints
    @PostMapping("/{id}/health-check")
    public ResponseEntity<HealthCheckDTO> triggerHealthCheck(@PathVariable Long id) {
        log.info("Triggering health check for service: {}", id);
        return ResponseEntity.ok(healthMonitorService.performHealthCheck(id));
    }

    @GetMapping("/{id}/health-checks")
    public ResponseEntity<List<HealthCheckDTO>> getRecentHealthChecks(
            @PathVariable Long id,
            @RequestParam(defaultValue = "24") int hours) {
        return ResponseEntity.ok(healthMonitorService.getRecentHealthChecks(id, hours));
    }

    @GetMapping("/{id}/health-checks/latest")
    public ResponseEntity<HealthCheckDTO> getLatestHealthCheck(@PathVariable Long id) {
        HealthCheckDTO healthCheck = healthMonitorService.getLatestHealthCheck(id);
        return healthCheck != null ? ResponseEntity.ok(healthCheck) : ResponseEntity.notFound().build();
    }

    // Metrics Endpoints
    @PostMapping("/{id}/metrics")
    public ResponseEntity<MetricsDTO> triggerMetricsCollection(@PathVariable Long id) {
        log.info("Triggering metrics collection for service: {}", id);
        return ResponseEntity.ok(metricsCollectorService.collectMetrics(id));
    }

    @GetMapping("/{id}/metrics")
    public ResponseEntity<List<MetricsDTO>> getRecentMetrics(
            @PathVariable Long id,
            @RequestParam(defaultValue = "24") int hours) {
        return ResponseEntity.ok(metricsCollectorService.getRecentMetrics(id, hours));
    }

    @GetMapping("/{id}/metrics/latest")
    public ResponseEntity<MetricsDTO> getLatestMetrics(@PathVariable Long id) {
        MetricsDTO metrics = metricsCollectorService.getLatestMetrics(id);
        return metrics != null ? ResponseEntity.ok(metrics) : ResponseEntity.notFound().build();
    }

    @GetMapping("/{id}/metrics/cpu/average")
    public ResponseEntity<Double> getAverageCpuUsage(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") int hours) {
        Double avg = metricsCollectorService.getAverageCpuUsage(id, hours);
        return avg != null ? ResponseEntity.ok(avg) : ResponseEntity.ok(0.0);
    }

    @GetMapping("/{id}/metrics/memory/average")
    public ResponseEntity<Double> getAverageMemoryUsage(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") int hours) {
        Double avg = metricsCollectorService.getAverageMemoryUsage(id, hours);
        return avg != null ? ResponseEntity.ok(avg) : ResponseEntity.ok(0.0);
    }

    // AI Analysis Endpoint
    @GetMapping("/{id}/analysis")
    public ResponseEntity<AIAnalysisDTO> getAIAnalysis(@PathVariable Long id) {
        log.info("Requesting AI analysis for service: {}", id);
        return ResponseEntity.ok(aiService.analyzeService(id));
    }

    @GetMapping("/{id}/stability-score")
    public ResponseEntity<Integer> getStabilityScore(@PathVariable Long id) {
        return ResponseEntity.ok(aiService.calculateStabilityScore(id));
    }
}

