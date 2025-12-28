package com.management.console.service;

import com.management.console.domain.enums.HealthStatus;
import com.management.console.dto.AuditLogDTO;
import com.management.console.dto.DashboardDTO;
import com.management.console.dto.IncidentDTO;
import com.management.console.repository.IncidentRepository;
import com.management.console.repository.ManagedServiceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class DashboardService {

    private final ManagedServiceRepository serviceRepository;
    private final IncidentRepository incidentRepository;
    private final ServiceRegistryService serviceRegistryService;
    private final IncidentService incidentService;
    private final AuditService auditService;

    public DashboardDTO getDashboardSummary() {
        log.debug("Building dashboard summary");

        // Service counts
        long totalServices = serviceRepository.count();
        Map<String, Long> typeDistribution = serviceRegistryService.getServiceTypeDistribution();
        long backendServices = typeDistribution.getOrDefault("BACKEND", 0L);
        long frontendServices = typeDistribution.getOrDefault("FRONTEND", 0L);

        // Health distribution
        Map<String, Long> healthDistribution = serviceRegistryService.getHealthDistribution();
        long healthyCount = healthDistribution.getOrDefault("HEALTHY", 0L);
        long degradedCount = healthDistribution.getOrDefault("DEGRADED", 0L);
        long criticalCount = healthDistribution.getOrDefault("CRITICAL", 0L);
        long downCount = healthDistribution.getOrDefault("DOWN", 0L);
        long unknownCount = healthDistribution.getOrDefault("UNKNOWN", 0L);

        // Incidents
        long activeIncidents = incidentService.countActiveIncidents();
        long incidentsToday = incidentService.countIncidentsSince(24);
        
        // Count critical active incidents
        List<IncidentDTO> activeIncidentsList = incidentService.getActiveIncidents();
        long criticalIncidents = activeIncidentsList.stream()
                .filter(i -> "CRITICAL".equals(i.getSeverity().name()))
                .count();

        // Actions today
        long actionsToday = auditService.countActionsSince(24);
        List<AuditLogDTO> failedActions = auditService.getFailedActions(24);
        long failedActionsToday = failedActions.size();

        // Risk summary
        long highRiskServices = serviceRepository.findServicesWithHighRisk(70).size();
        
        Double avgStability = serviceRepository.findAll().stream()
                .filter(s -> s.getStabilityScore() != null)
                .mapToInt(s -> s.getStabilityScore())
                .average()
                .orElse(100.0);

        // Recent actions
        List<AuditLogDTO> recentActions = auditService.getRecentActions(24);

        return DashboardDTO.builder()
                .totalServices(totalServices)
                .backendServices(backendServices)
                .frontendServices(frontendServices)
                .healthyCount(healthyCount)
                .degradedCount(degradedCount)
                .criticalCount(criticalCount)
                .downCount(downCount)
                .unknownCount(unknownCount)
                .activeIncidents(activeIncidents)
                .criticalIncidents(criticalIncidents)
                .incidentsToday(incidentsToday)
                .actionsToday(actionsToday)
                .failedActionsToday(failedActionsToday)
                .highRiskServices(highRiskServices)
                .averageStabilityScore(avgStability)
                .recentActions(recentActions.size() > 10 ? recentActions.subList(0, 10) : recentActions)
                .activeIncidentsList(activeIncidentsList)
                .healthDistribution(healthDistribution)
                .serviceTypeDistribution(typeDistribution)
                .build();
    }

    public Map<String, Long> getHealthDistribution() {
        return serviceRegistryService.getHealthDistribution();
    }

    public Map<String, Long> getServiceTypeDistribution() {
        return serviceRegistryService.getServiceTypeDistribution();
    }
}

