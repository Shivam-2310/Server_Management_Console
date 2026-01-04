package com.management.console.controller;

import com.management.console.domain.enums.HealthStatus;
import com.management.console.domain.enums.ServiceType;
import com.management.console.dto.*;
import com.management.console.exception.DuplicateResourceException;
import com.management.console.service.HealthMonitorService;
import com.management.console.service.MetricsCollectorService;
import com.management.console.service.ServiceRegistryService;
import com.management.console.service.RemoteLogCollectionService;
import com.management.console.service.InfrastructureMonitoringService;
import com.management.console.service.EnvironmentService;
import com.management.console.service.ActuatorDiagnosticService;
import com.management.console.service.ai.AIIntelligenceService;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.ArrayList;
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
    private final RemoteLogCollectionService remoteLogCollectionService;
    private final InfrastructureMonitoringService infrastructureMonitoringService;
    private final EnvironmentService environmentService;
    private final ActuatorDiagnosticService actuatorDiagnosticService;
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
        try {
            log.debug("Fetching all services");
            List<ServiceDTO> services = serviceRegistryService.getAllServices();
            log.debug("Found {} services", services.size());
            return ResponseEntity.ok(services);
        } catch (Exception e) {
            log.error("Error fetching all services: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to fetch services: " + e.getMessage(), e);
        }
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

    // Remote Log Collection Endpoints
    @GetMapping(value = "/{id}/logs", produces = "application/json")
    public ResponseEntity<List<com.management.console.service.RemoteLogCollectionService.RemoteLogEntry>> getServiceLogs(
            @PathVariable Long id,
            @RequestParam(defaultValue = "100") Integer lines,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String instanceId) {
        log.info("=== API CALL: GET /api/services/{}/logs ===", id);
        log.info("Parameters: lines={}, level={}, instanceId={}", lines, level, instanceId);
        
        try {
            // Default to "ALL" if level is null or empty to show all log levels
            String logLevel = (level != null && !level.trim().isEmpty()) ? level : "ALL";
            log.info("Using log level filter: {}", logLevel);
            
            List<com.management.console.service.RemoteLogCollectionService.RemoteLogEntry> logs = 
                    remoteLogCollectionService.collectLogsFromService(id, lines, logLevel)
                            .block(Duration.ofSeconds(60)); // Block to get the result synchronously
            
            log.info("=== API RESPONSE: Returning {} log entries for service {} ===", 
                    logs != null ? logs.size() : 0, id);
            
            if (logs == null) {
                log.warn("WARNING: Null log list returned for service {}", id);
                logs = new java.util.ArrayList<>();
            } else if (logs.isEmpty()) {
                log.warn("WARNING: Empty log list returned for service {}", id);
                log.warn("This could mean:");
                log.warn("  1. The actuator /logfile endpoint returned empty content");
                log.warn("  2. The log parsing failed");
                log.warn("  3. The service's logfile endpoint is not accessible");
            } else {
                log.info("First log entry: serviceId={}, level={}, message={}", 
                        logs.get(0).getServiceId(), 
                        logs.get(0).getLevel(),
                        logs.get(0).getMessage() != null ? 
                                logs.get(0).getMessage().substring(0, Math.min(100, logs.get(0).getMessage().length())) 
                                : "null");
            }
            
            return ResponseEntity.ok()
                    .header("Content-Type", "application/json")
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .body(logs);
        } catch (Exception e) {
            log.error("=== API ERROR: Failed to collect logs from service {} ===", id, e);
            log.error("Error type: {}, Message: {}", e.getClass().getSimpleName(), e.getMessage());
            if (e.getCause() != null) {
                log.error("Caused by: {}", e.getCause().getMessage());
            }
            return ResponseEntity.ok()
                    .header("Content-Type", "application/json")
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .body(new java.util.ArrayList<>());
        }
    }

    @GetMapping("/{id}/logs/search")
    public Mono<ResponseEntity<List<com.management.console.service.RemoteLogCollectionService.RemoteLogEntry>>> searchServiceLogs(
            @PathVariable Long id,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime,
            @RequestParam(required = false) String level,
            @RequestParam(defaultValue = "100") Integer maxResults) {
        // Parse time parameters if provided
        java.time.LocalDateTime start = startTime != null ? 
            java.time.LocalDateTime.parse(startTime) : null;
        java.time.LocalDateTime end = endTime != null ? 
            java.time.LocalDateTime.parse(endTime) : null;
        
        return remoteLogCollectionService.searchLogs(id, query, start, end, level, maxResults)
                .map(ResponseEntity::ok)
                .onErrorResume(WebClientResponseException.class, e -> {
                    log.error("Failed to search logs from service {}: {}", id, e.getMessage());
                    return Mono.just(ResponseEntity.status(e.getStatusCode()).build());
                });
    }

    @GetMapping("/{id}/logs/statistics")
    public Mono<ResponseEntity<com.management.console.service.RemoteLogCollectionService.LogStatistics>> getLogStatistics(
            @PathVariable Long id,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime) {
        java.time.LocalDateTime start = startTime != null ? 
            java.time.LocalDateTime.parse(startTime) : null;
        java.time.LocalDateTime end = endTime != null ? 
            java.time.LocalDateTime.parse(endTime) : null;
        
        return remoteLogCollectionService.getLogStatistics(id, start, end)
                .map(ResponseEntity::ok)
                .onErrorResume(WebClientResponseException.class, e -> {
                    log.error("Failed to get log statistics for service {}: {}", id, e.getMessage());
                    return Mono.just(ResponseEntity.status(e.getStatusCode()).build());
                });
    }

    // Configuration Endpoints
    @GetMapping(value = "/{id}/configuration", produces = "application/json")
    public ResponseEntity<com.management.console.service.EnvironmentService.EnvironmentInfo> getServiceConfiguration(@PathVariable Long id) {
        log.info("=== API CALL: GET /api/services/{}/configuration ===", id);
        try {
            com.management.console.service.EnvironmentService.EnvironmentInfo config = 
                    environmentService.getRemoteEnvironment(id)
                            .switchIfEmpty(Mono.just(createEmptyEnvironmentInfo()))
                            .map(c -> {
                                log.info("=== API RESPONSE: Configuration for service {} ===", id);
                                log.info("Property sources: {}", c.getPropertySources() != null ? c.getPropertySources().size() : 0);
                                if (c.getPropertySources() != null && !c.getPropertySources().isEmpty()) {
                                    c.getPropertySources().forEach(ps -> {
                                        log.info("  - {}: {} properties", ps.getName(), ps.getPropertyCount());
                                    });
                                } else {
                                    log.warn("WARNING: No property sources in configuration response");
                                }
                                // Ensure required fields are set
                                if (c.getTimestamp() == 0L) {
                                    c.setTimestamp(System.currentTimeMillis());
                                }
                                if (c.getActiveProfiles() == null) c.setActiveProfiles(new java.util.ArrayList<>());
                                if (c.getPropertySources() == null) c.setPropertySources(new java.util.ArrayList<>());
                                log.info("Response with {} property sources", c.getPropertySources().size());
                                return c;
                            })
                            .doOnNext(c -> {
                                log.info("=== CONFIG RESPONSE READY ===");
                                log.info("Property sources: {}", 
                                        c.getPropertySources() != null ? c.getPropertySources().size() : 0);
                            })
                            .doOnError(error -> {
                                log.error("=== ERROR in configuration Mono chain ===", error);
                            })
                            .onErrorResume(Exception.class, e -> {
                                log.error("Failed to get configuration from service {}: {}", id, e.getMessage(), e);
                                return Mono.just(createEmptyEnvironmentInfo());
                            })
                            .block(Duration.ofSeconds(30)); // Block to get the result
            
            // Ensure body is never null
            if (config == null) {
                log.error("ERROR: Configuration info is NULL! Creating empty config.");
                config = createEmptyEnvironmentInfo();
            }
            
            log.info("Response body before serialization - timestamp: {}, propertySources: {}", 
                    config.getTimestamp(), config.getPropertySources() != null ? config.getPropertySources().size() : 0);
            log.info("=== CONFIG RESPONSE RETURNING ===");
            
            return ResponseEntity.ok()
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .body(config);
        } catch (Exception e) {
            log.error("Exception in getServiceConfiguration: {}", e.getMessage(), e);
            return ResponseEntity.ok()
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .body(createEmptyEnvironmentInfo());
        }
    }

    // Infrastructure Monitoring Endpoints
    @GetMapping(value = "/{id}/infrastructure", produces = "application/json")
    public ResponseEntity<com.management.console.service.InfrastructureMonitoringService.InfrastructureInfo> getInfrastructureInfo(@PathVariable Long id) {
        log.info("=== API CALL: GET /api/services/{}/infrastructure ===", id);
        try {
            com.management.console.service.InfrastructureMonitoringService.InfrastructureInfo infra = 
                    infrastructureMonitoringService.getRemoteInfrastructureInfo(id)
                            .switchIfEmpty(Mono.just(createEmptyInfrastructureInfo(id)))
                            .map(i -> {
                                log.info("=== API RESPONSE: Infrastructure for service {} ===", id);
                                log.info("Service ID: {}, Service Name: {}", i.getServiceId(), i.getServiceName());
                                log.info("OS: {} {}, Arch: {}", i.getOsName(), i.getOsVersion(), i.getOsArch());
                                log.info("JVM: {} {} ({})", i.getJvmName(), i.getJvmVersion(), i.getJvmVendor());
                                log.info("CPU: System={}%, Process={}%", i.getSystemCpuLoad(), i.getProcessCpuLoad());
                                log.info("Memory: Total={}, Free={}", i.getTotalPhysicalMemory(), i.getFreePhysicalMemory());
                                
                                // Ensure at least service info is set
                                if (i.getServiceId() == null || i.getServiceName() == null) {
                                    try {
                                        var service = serviceRegistryService.getService(id);
                                        if (i.getServiceId() == null) i.setServiceId(service.getId());
                                        if (i.getServiceName() == null) i.setServiceName(service.getName());
                                        if (i.getInstanceId() == null) i.setInstanceId(service.getInstanceId());
                                    } catch (Exception e) {
                                        log.warn("Could not set service info: {}", e.getMessage());
                                    }
                                }
                                
                                // Ensure we have at least some non-null values for proper JSON serialization
                                if (i.getOsName() == null) i.setOsName("Unknown");
                                if (i.getJvmName() == null) i.setJvmName("Unknown");
                                if (i.getJvmVersion() == null) i.setJvmVersion("Unknown");
                                
                                log.info("Final infrastructure object - Service: {}, OS: {}, JVM: {}", 
                                        i.getServiceName(), i.getOsName(), i.getJvmName());
                                return i;
                            })
                            .doOnNext(i -> {
                                log.info("=== INFRA RESPONSE READY ===");
                                log.info("Body service: {}, OS: {}, JVM: {}, CPU: {}%", 
                                        i.getServiceName(), i.getOsName(), i.getJvmName(), i.getSystemCpuLoad());
                            })
                            .doOnError(error -> {
                                log.error("=== ERROR in infrastructure Mono chain ===", error);
                            })
                            .onErrorResume(Exception.class, e -> {
                                log.error("Failed to get infrastructure info from service {}: {}", id, e.getMessage(), e);
                                return Mono.just(createEmptyInfrastructureInfo(id));
                            })
                            .block(Duration.ofSeconds(30)); // Block to get the result
            
            // Ensure body is never null
            if (infra == null) {
                log.error("ERROR: Infrastructure info is NULL! Creating empty info.");
                infra = createEmptyInfrastructureInfo(id);
            }
            
            log.info("Response body before serialization - serviceId: {}, serviceName: {}, osName: {}", 
                    infra.getServiceId(), infra.getServiceName(), infra.getOsName());
            log.info("=== INFRA RESPONSE RETURNING ===");
            
            return ResponseEntity.ok()
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .body(infra);
        } catch (Exception e) {
            log.error("Exception in getInfrastructureInfo: {}", e.getMessage(), e);
            return ResponseEntity.ok()
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .body(createEmptyInfrastructureInfo(id));
        }
    }

    @GetMapping("/{id}/jvm")
    public ResponseEntity<com.management.console.service.InfrastructureMonitoringService.JvmInfo> getJvmInfo(@PathVariable Long id) {
        log.info("=== API CALL: GET /api/services/{}/jvm ===", id);
        try {
            com.management.console.service.InfrastructureMonitoringService.JvmInfo jvmInfo = 
                    infrastructureMonitoringService.getRemoteJvmInfo(id)
                            .block(Duration.ofSeconds(30)); // Block to get the result synchronously
            
            if (jvmInfo == null) {
                log.warn("JVM info is null for service {}", id);
                jvmInfo = createEmptyJvmInfo(id);
            }
            
            log.info("=== API RESPONSE: JVM info for service {} ===", id);
            log.info("JVM Name: {}, Version: {}, Vendor: {}", 
                    jvmInfo.getJvmName(), jvmInfo.getJvmVersion(), jvmInfo.getJvmVendor());
            log.info("Heap: {}/{} bytes, Non-Heap: {} bytes", 
                    jvmInfo.getHeapUsed(), jvmInfo.getHeapMax(), jvmInfo.getNonHeapUsed());
            log.info("Threads: {}, Uptime: {} ms", 
                    jvmInfo.getThreadCount(), jvmInfo.getUptime());
            
            // Ensure at least service info is set
            if (jvmInfo.getServiceId() == null || jvmInfo.getServiceName() == null) {
                try {
                    var service = serviceRegistryService.getService(id);
                    if (jvmInfo.getServiceId() == null) jvmInfo.setServiceId(service.getId());
                    if (jvmInfo.getServiceName() == null) jvmInfo.setServiceName(service.getName());
                    if (jvmInfo.getInstanceId() == null) jvmInfo.setInstanceId(service.getInstanceId());
                } catch (Exception e) {
                    log.warn("Could not set service info: {}", e.getMessage());
                }
            }
            
            return ResponseEntity.ok()
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .body(jvmInfo);
        } catch (Exception e) {
            log.error("=== API ERROR: Failed to get JVM info from service {} ===", id, e);
            log.error("Error type: {}, Message: {}", e.getClass().getSimpleName(), e.getMessage());
            if (e.getCause() != null) {
                log.error("Caused by: {}", e.getCause().getMessage());
            }
            return ResponseEntity.ok()
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .body(createEmptyJvmInfo(id));
        }
    }
    
    private com.management.console.service.InfrastructureMonitoringService.JvmInfo createEmptyJvmInfo(Long id) {
        com.management.console.service.InfrastructureMonitoringService.JvmInfo emptyJvm = 
                new com.management.console.service.InfrastructureMonitoringService.JvmInfo();
        try {
            var service = serviceRegistryService.getService(id);
            emptyJvm.setServiceId(service.getId());
            emptyJvm.setServiceName(service.getName());
            emptyJvm.setInstanceId(service.getInstanceId());
        } catch (Exception e) {
            log.warn("Could not set service info for empty JVM: {}", e.getMessage());
            emptyJvm.setServiceId(id);
            emptyJvm.setServiceName("Unknown");
        }
        return emptyJvm;
    }

    // Diagnostic endpoint to test actuator connectivity
    @GetMapping("/{id}/test-actuator")
    public ResponseEntity<java.util.Map<String, Object>> testActuatorConnectivity(@PathVariable Long id) {
        log.info("Testing actuator connectivity for service {}", id);
        try {
            var service = serviceRegistryService.getServiceEntity(id);
            String baseUrl = service.getActuatorUrl();
            
            java.util.Map<String, Object> testUrls = new java.util.HashMap<>();
            testUrls.put("logfile", baseUrl + "/logfile");
            testUrls.put("env", baseUrl + "/env");
            testUrls.put("metrics", baseUrl + "/metrics");
            testUrls.put("info", baseUrl + "/info");
            
            java.util.Map<String, Object> results = new java.util.HashMap<>();
            results.put("serviceId", id);
            results.put("serviceName", service.getName());
            results.put("actuatorBaseUrl", baseUrl);
            results.put("testUrls", testUrls);
            
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            log.error("Error testing actuator connectivity: {}", e.getMessage(), e);
            java.util.Map<String, Object> errorResponse = new java.util.HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.status(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(errorResponse);
        }
    }

    // Detailed diagnostic endpoint
    @GetMapping("/{id}/diagnose/{endpoint}")
    public Mono<ResponseEntity<com.management.console.service.ActuatorDiagnosticService.DiagnosticResult>> diagnoseActuatorEndpoint(
            @PathVariable Long id,
            @PathVariable String endpoint) {
        log.info("Diagnosing actuator endpoint {} for service {}", endpoint, id);
        return actuatorDiagnosticService.testActuatorEndpoint(id, "/" + endpoint)
                .map(ResponseEntity::ok)
                .onErrorResume(e -> {
                    log.error("Diagnostic failed: {}", e.getMessage(), e);
                    return Mono.just(ResponseEntity.status(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(new com.management.console.service.ActuatorDiagnosticService.DiagnosticResult()));
                });
    }
    
    // Helper methods to create empty responses
    private com.management.console.service.InfrastructureMonitoringService.InfrastructureInfo createEmptyInfrastructureInfo(Long serviceId) {
        com.management.console.service.InfrastructureMonitoringService.InfrastructureInfo info = 
                new com.management.console.service.InfrastructureMonitoringService.InfrastructureInfo();
        try {
            var service = serviceRegistryService.getService(serviceId);
            info.setServiceId(service.getId());
            info.setServiceName(service.getName());
            info.setInstanceId(service.getInstanceId());
        } catch (Exception e) {
            info.setServiceId(serviceId);
            info.setServiceName("Unknown");
        }
        info.setOsName("Unknown");
        info.setOsVersion("Unknown");
        info.setOsArch("Unknown");
        info.setJvmName("Unknown");
        info.setJvmVersion("Unknown");
        info.setJvmVendor("Unknown");
        info.setAvailableProcessors(0);
        info.setSystemCpuLoad(0.0);
        info.setProcessCpuLoad(0.0);
        return info;
    }
    
    private com.management.console.service.EnvironmentService.EnvironmentInfo createEmptyEnvironmentInfo() {
        com.management.console.service.EnvironmentService.EnvironmentInfo info = 
                new com.management.console.service.EnvironmentService.EnvironmentInfo();
        info.setTimestamp(System.currentTimeMillis());
        info.setActiveProfiles(new ArrayList<>());
        info.setPropertySources(new ArrayList<>());
        return info;
    }
}

