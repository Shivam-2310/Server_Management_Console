package com.management.console.dto;

import lombok.*;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DashboardDTO {
    // Service counts
    private Long totalServices;
    private Long backendServices;
    private Long frontendServices;
    
    // Health distribution
    private Long healthyCount;
    private Long degradedCount;
    private Long criticalCount;
    private Long downCount;
    private Long unknownCount;
    
    // Incidents
    private Long activeIncidents;
    private Long criticalIncidents;
    private Long incidentsToday;
    
    // Actions
    private Long actionsToday;
    private Long failedActionsToday;
    
    // Risk summary
    private Long highRiskServices;
    private Double averageStabilityScore;
    
    // Recent activity
    private List<AuditLogDTO> recentActions;
    private List<IncidentDTO> activeIncidentsList;
    
    // Service health map
    private Map<String, Long> healthDistribution;
    private Map<String, Long> serviceTypeDistribution;
}

