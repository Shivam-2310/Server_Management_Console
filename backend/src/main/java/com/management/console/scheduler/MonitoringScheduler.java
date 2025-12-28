package com.management.console.scheduler;

import com.management.console.domain.entity.ManagedService;
import com.management.console.domain.enums.HealthStatus;
import com.management.console.domain.enums.ServiceType;
import com.management.console.dto.AIAnalysisDTO;
import com.management.console.dto.HealthCheckDTO;
import com.management.console.repository.ManagedServiceRepository;
import com.management.console.service.HealthMonitorService;
import com.management.console.service.IncidentService;
import com.management.console.service.MetricsCollectorService;
import com.management.console.service.ServiceRegistryService;
import com.management.console.service.ai.AIIntelligenceService;
import com.management.console.websocket.DashboardWebSocketHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class MonitoringScheduler {

    private final ManagedServiceRepository serviceRepository;
    private final HealthMonitorService healthMonitorService;
    private final MetricsCollectorService metricsCollectorService;
    private final AIIntelligenceService aiService;
    private final IncidentService incidentService;
    private final ServiceRegistryService serviceRegistryService;
    private final DashboardWebSocketHandler webSocketHandler;

    @Value("${app.monitoring.health-check-interval:10000}")
    private long healthCheckInterval;

    @Value("${app.monitoring.metrics-poll-interval:15000}")
    private long metricsPollInterval;

    @Value("${app.monitoring.frontend-check-interval:30000}")
    private long frontendCheckInterval;

    /**
     * Health check for backend services - every 10 seconds
     */
    @Scheduled(fixedRateString = "${app.monitoring.health-check-interval:10000}")
    public void performBackendHealthChecks() {
        log.debug("Running scheduled backend health checks");
        
        List<ManagedService> backendServices = serviceRepository
                .findByServiceTypeAndEnabledTrue(ServiceType.BACKEND);
        
        for (ManagedService service : backendServices) {
            try {
                HealthStatus previousStatus = service.getHealthStatus();
                HealthCheckDTO result = healthMonitorService.performHealthCheck(service);
                
                // Check for status change and create incident if degraded
                if (previousStatus != result.getStatus() && 
                    result.getStatus() != HealthStatus.HEALTHY &&
                    result.getStatus() != HealthStatus.UNKNOWN) {
                    
                    incidentService.createIncidentFromHealthChange(service, previousStatus, result.getStatus());
                }
                
                // Auto-resolve if healthy
                if (result.getStatus() == HealthStatus.HEALTHY) {
                    incidentService.autoResolveIfHealthy(service);
                }
                
                // Notify WebSocket clients
                webSocketHandler.broadcastHealthUpdate(service.getId(), result);
                
            } catch (Exception e) {
                log.error("Health check failed for service {}: {}", service.getName(), e.getMessage());
            }
        }
    }

    /**
     * Health check for frontend services - every 30 seconds
     */
    @Scheduled(fixedRateString = "${app.monitoring.frontend-check-interval:30000}")
    public void performFrontendHealthChecks() {
        log.debug("Running scheduled frontend health checks");
        
        List<ManagedService> frontendServices = serviceRepository
                .findByServiceTypeAndEnabledTrue(ServiceType.FRONTEND);
        
        for (ManagedService service : frontendServices) {
            try {
                HealthStatus previousStatus = service.getHealthStatus();
                HealthCheckDTO result = healthMonitorService.performHealthCheck(service);
                
                if (previousStatus != result.getStatus() && 
                    result.getStatus() != HealthStatus.HEALTHY) {
                    incidentService.createIncidentFromHealthChange(service, previousStatus, result.getStatus());
                }
                
                if (result.getStatus() == HealthStatus.HEALTHY) {
                    incidentService.autoResolveIfHealthy(service);
                }
                
                webSocketHandler.broadcastHealthUpdate(service.getId(), result);
                
            } catch (Exception e) {
                log.error("Frontend health check failed for service {}: {}", service.getName(), e.getMessage());
            }
        }
    }

    /**
     * Metrics collection - every 15 seconds
     */
    @Scheduled(fixedRateString = "${app.monitoring.metrics-poll-interval:15000}")
    public void collectMetrics() {
        log.debug("Running scheduled metrics collection");
        
        List<ManagedService> services = serviceRepository.findByEnabledTrue();
        
        for (ManagedService service : services) {
            try {
                metricsCollectorService.collectMetrics(service);
            } catch (Exception e) {
                log.error("Metrics collection failed for service {}: {}", service.getName(), e.getMessage());
            }
        }
    }

    /**
     * AI-driven anomaly detection - every 60 seconds
     */
    @Scheduled(fixedRate = 60000)
    public void performAnomalyDetection() {
        if (!aiService.isAIAvailable()) {
            log.debug("AI service not available, skipping anomaly detection");
            return;
        }
        
        log.debug("Running AI anomaly detection");
        
        List<ManagedService> services = serviceRepository.findByEnabledTrue();
        
        for (ManagedService service : services) {
            try {
                AIAnalysisDTO analysis = aiService.analyzeService(service.getId());
                
                // Create incident if anomaly detected
                if (analysis.getAnomalyDetected() != null && analysis.getAnomalyDetected()) {
                    incidentService.createIncidentFromAnomaly(
                            service, 
                            analysis.getAnomalyType(), 
                            analysis.getAnomalyDescription()
                    );
                }
                
                // Update risk scores
                serviceRegistryService.updateRiskScores(
                        service.getId(),
                        aiService.calculateStabilityScore(service.getId()),
                        analysis.getRiskScore(),
                        analysis.getRiskTrend()
                );
                
            } catch (Exception e) {
                log.error("AI analysis failed for service {}: {}", service.getName(), e.getMessage());
            }
        }
    }

    /**
     * Stability score recalculation - every 5 minutes
     */
    @Scheduled(fixedRate = 300000)
    public void recalculateStabilityScores() {
        log.debug("Recalculating stability scores");
        
        List<ManagedService> services = serviceRepository.findByEnabledTrue();
        
        for (ManagedService service : services) {
            try {
                Integer stabilityScore = aiService.calculateStabilityScore(service.getId());
                service.setStabilityScore(stabilityScore);
                serviceRepository.save(service);
            } catch (Exception e) {
                log.error("Stability calculation failed for service {}: {}", service.getName(), e.getMessage());
            }
        }
    }

    /**
     * Cleanup old data - daily at 2 AM
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void cleanupOldData() {
        log.info("Running daily cleanup of old data");
        
        // Keep 7 days of metrics
        metricsCollectorService.cleanupOldMetrics(7);
        
        log.info("Cleanup completed");
    }

    /**
     * Dashboard summary broadcast - every 5 seconds
     */
    @Scheduled(fixedRate = 5000)
    public void broadcastDashboardSummary() {
        try {
            webSocketHandler.broadcastDashboardSummary();
        } catch (Exception e) {
            log.error("Failed to broadcast dashboard summary: {}", e.getMessage());
        }
    }
}

