package com.management.console.mapper;

import com.management.console.domain.entity.*;
import com.management.console.dto.*;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class ServiceMapper {

    public ServiceDTO toDTO(ManagedService service) {
        if (service == null) return null;
        
        // Ensure tags is never null
        List<String> tags = service.getTags() != null ? service.getTags() : new ArrayList<>();
        
        return ServiceDTO.builder()
                .id(service.getId())
                .name(service.getName())
                .description(service.getDescription())
                .serviceType(service.getServiceType())
                .healthStatus(service.getHealthStatus() != null ? service.getHealthStatus() : com.management.console.domain.enums.HealthStatus.UNKNOWN)
                .host(service.getHost())
                .port(service.getPort())
                .healthEndpoint(service.getHealthEndpoint())
                .metricsEndpoint(service.getMetricsEndpoint())
                .baseUrl(service.getBaseUrl())
                .actuatorBasePath(service.getActuatorBasePath())
                .frontendTechnology(service.getFrontendTechnology())
                .servingTechnology(service.getServingTechnology())
                .startCommand(service.getStartCommand())
                .stopCommand(service.getStopCommand())
                .restartCommand(service.getRestartCommand())
                .workingDirectory(service.getWorkingDirectory())
                .processIdentifier(service.getProcessIdentifier())
                .isRunning(service.getIsRunning() != null ? service.getIsRunning() : false)
                .instanceCount(service.getInstanceCount() != null ? service.getInstanceCount() : 1)
                .cpuUsage(service.getCpuUsage())
                .memoryUsage(service.getMemoryUsage())
                .responseTime(service.getResponseTime())
                .errorRate(service.getErrorRate())
                .stabilityScore(service.getStabilityScore() != null ? service.getStabilityScore() : 100)
                .riskScore(service.getRiskScore() != null ? service.getRiskScore() : 0)
                .riskTrend(service.getRiskTrend())
                .lastHealthCheck(service.getLastHealthCheck())
                .lastMetricsCollection(service.getLastMetricsCollection())
                .lastRestart(service.getLastRestart())
                .tags(tags)
                .environment(service.getEnvironment())
                .enabled(service.getEnabled() != null ? service.getEnabled() : true)
                .createdAt(service.getCreatedAt())
                .updatedAt(service.getUpdatedAt())
                .build();
    }

    public MetricsDTO toDTO(ServiceMetrics metrics) {
        if (metrics == null) return null;
        
        return MetricsDTO.builder()
                .id(metrics.getId())
                .serviceId(metrics.getService().getId())
                .serviceName(metrics.getService().getName())
                .timestamp(metrics.getTimestamp())
                .cpuUsage(metrics.getCpuUsage())
                .systemCpuUsage(metrics.getSystemCpuUsage())
                .cpuCount(metrics.getCpuCount())
                .memoryUsed(metrics.getMemoryUsed())
                .memoryMax(metrics.getMemoryMax())
                .memoryUsagePercent(metrics.getMemoryUsagePercent())
                .heapUsed(metrics.getHeapUsed())
                .heapMax(metrics.getHeapMax())
                .threadCount(metrics.getThreadCount())
                .threadPeakCount(metrics.getThreadPeakCount())
                .gcPauseCount(metrics.getGcPauseCount())
                .gcPauseTime(metrics.getGcPauseTime())
                .httpRequestsTotal(metrics.getHttpRequestsTotal())
                .httpRequestsPerSecond(metrics.getHttpRequestsPerSecond())
                .averageResponseTime(metrics.getAverageResponseTime())
                .p95ResponseTime(metrics.getP95ResponseTime())
                .p99ResponseTime(metrics.getP99ResponseTime())
                .errorCount(metrics.getErrorCount())
                .errorRate(metrics.getErrorRate())
                .uptimeSeconds(metrics.getUptimeSeconds())
                .pageLoadTime(metrics.getPageLoadTime())
                .firstContentfulPaint(metrics.getFirstContentfulPaint())
                .largestContentfulPaint(metrics.getLargestContentfulPaint())
                .build();
    }

    public HealthCheckDTO toDTO(HealthCheckResult result) {
        if (result == null) return null;
        
        return HealthCheckDTO.builder()
                .id(result.getId())
                .serviceId(result.getService().getId())
                .serviceName(result.getService().getName())
                .timestamp(result.getTimestamp())
                .status(result.getStatus())
                .responseTimeMs(result.getResponseTimeMs())
                .httpStatusCode(result.getHttpStatusCode())
                .statusMessage(result.getStatusMessage())
                .diskSpaceFree(result.getDiskSpaceFree())
                .diskSpaceTotal(result.getDiskSpaceTotal())
                .databaseHealthy(result.getDatabaseHealthy())
                .checkType(result.getCheckType())
                .errorMessage(result.getErrorMessage())
                .build();
    }

    public AuditLogDTO toDTO(AuditLog auditLog) {
        if (auditLog == null) return null;
        
        return AuditLogDTO.builder()
                .id(auditLog.getId())
                .serviceId(auditLog.getService() != null ? auditLog.getService().getId() : null)
                .serviceName(auditLog.getService() != null ? auditLog.getService().getName() : null)
                .timestamp(auditLog.getTimestamp())
                .username(auditLog.getUsername())
                .userRole(auditLog.getUserRole())
                .ipAddress(auditLog.getIpAddress())
                .action(auditLog.getAction())
                .actionDetails(auditLog.getActionDetails())
                .durationMs(auditLog.getDurationMs())
                .reason(auditLog.getReason())
                .isAutomated(auditLog.getIsAutomated())
                .status(auditLog.getStatus())
                .resultMessage(auditLog.getResultMessage())
                .aiRecommended(auditLog.getAiRecommended())
                .aiRecommendation(auditLog.getAiRecommendation())
                .riskLevel(auditLog.getRiskLevel())
                .build();
    }

    public IncidentDTO toDTO(Incident incident) {
        if (incident == null) return null;
        
        return IncidentDTO.builder()
                .id(incident.getId())
                .serviceId(incident.getService().getId())
                .serviceName(incident.getService().getName())
                .title(incident.getTitle())
                .description(incident.getDescription())
                .severity(incident.getSeverity())
                .status(incident.getStatus())
                .detectionSource(incident.getDetectionSource())
                .aiSummary(incident.getAiSummary())
                .aiRecommendation(incident.getAiRecommendation())
                .aiConfidence(incident.getAiConfidence())
                .createdAt(incident.getCreatedAt())
                .acknowledgedAt(incident.getAcknowledgedAt())
                .acknowledgedBy(incident.getAcknowledgedBy())
                .resolvedAt(incident.getResolvedAt())
                .resolvedBy(incident.getResolvedBy())
                .resolution(incident.getResolution())
                .affectedUsers(incident.getAffectedUsers())
                .errorRateIncrease(incident.getErrorRateIncrease())
                .latencyIncrease(incident.getLatencyIncrease())
                .tags(incident.getTags())
                .build();
    }
}

