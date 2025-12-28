package com.management.console.service;

import com.management.console.domain.entity.Incident;
import com.management.console.domain.entity.ManagedService;
import com.management.console.domain.entity.ServiceMetrics;
import com.management.console.domain.enums.HealthStatus;
import com.management.console.domain.enums.IncidentSeverity;
import com.management.console.dto.IncidentDTO;
import com.management.console.mapper.ServiceMapper;
import com.management.console.repository.IncidentRepository;
import com.management.console.repository.ServiceMetricsRepository;
import com.management.console.service.ai.AIIntelligenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class IncidentService {

    private final IncidentRepository incidentRepository;
    private final ServiceMetricsRepository metricsRepository;
    private final AIIntelligenceService aiService;
    private final ServiceMapper serviceMapper;

    public IncidentDTO createIncident(ManagedService service, String title, String description,
                                       IncidentSeverity severity, String detectionSource) {
        log.info("Creating incident for service {}: {}", service.getName(), title);

        // Get current metrics for context
        ServiceMetrics latestMetrics = metricsRepository.findTopByServiceIdOrderByTimestampDesc(service.getId())
                .orElse(null);

        Incident incident = Incident.builder()
                .service(service)
                .title(title)
                .description(description)
                .severity(severity)
                .status("OPEN")
                .detectionSource(detectionSource)
                .build();

        // Capture metrics at incident time
        if (latestMetrics != null) {
            incident.setCpuAtIncident(latestMetrics.getCpuUsage());
            incident.setMemoryAtIncident(latestMetrics.getMemoryUsagePercent());
            incident.setErrorRateAtIncident(latestMetrics.getErrorRate());
            incident.setResponseTimeAtIncident(
                    latestMetrics.getAverageResponseTime() != null ? 
                    latestMetrics.getAverageResponseTime().longValue() : null);
        }

        Incident saved = incidentRepository.save(incident);

        // Generate AI summary asynchronously
        try {
            List<ServiceMetrics> recentMetrics = metricsRepository.findRecentMetrics(
                    service.getId(), LocalDateTime.now().minusHours(1));
            String aiSummary = aiService.generateIncidentSummary(saved, recentMetrics);
            if (aiSummary != null) {
                saved.setAiSummary(aiSummary);
                saved = incidentRepository.save(saved);
            }
        } catch (Exception e) {
            log.warn("Failed to generate AI summary for incident: {}", e.getMessage());
        }

        log.info("Incident created with ID: {}", saved.getId());
        return serviceMapper.toDTO(saved);
    }

    public IncidentDTO createIncidentFromHealthChange(ManagedService service, 
                                                       HealthStatus previousStatus, 
                                                       HealthStatus newStatus) {
        String title = String.format("%s transitioned from %s to %s", 
                service.getName(), previousStatus, newStatus);
        
        IncidentSeverity severity = switch (newStatus) {
            case DOWN -> IncidentSeverity.CRITICAL;
            case CRITICAL -> IncidentSeverity.HIGH;
            case DEGRADED -> IncidentSeverity.MEDIUM;
            default -> IncidentSeverity.LOW;
        };

        String description = String.format(
                "Service %s (%s) health status changed from %s to %s. " +
                "Environment: %s. Last health check at: %s",
                service.getName(),
                service.getServiceType(),
                previousStatus,
                newStatus,
                service.getEnvironment(),
                service.getLastHealthCheck()
        );

        return createIncident(service, title, description, severity, "HEALTH_CHECK");
    }

    public IncidentDTO createIncidentFromAnomaly(ManagedService service, String anomalyType, 
                                                  String anomalyDescription) {
        String title = String.format("Anomaly detected: %s on %s", anomalyType, service.getName());
        
        IncidentSeverity severity = switch (anomalyType) {
            case "CPU_SATURATION", "ERROR_SPIKE" -> IncidentSeverity.HIGH;
            case "MEMORY_LEAK" -> IncidentSeverity.MEDIUM;
            default -> IncidentSeverity.LOW;
        };

        return createIncident(service, title, anomalyDescription, severity, "AI_ANOMALY");
    }

    public IncidentDTO acknowledgeIncident(Long incidentId, String username) {
        log.info("Acknowledging incident {} by user {}", incidentId, username);
        
        Incident incident = getIncidentEntity(incidentId);
        incident.setStatus("INVESTIGATING");
        incident.setAcknowledgedAt(LocalDateTime.now());
        incident.setAcknowledgedBy(username);
        
        return serviceMapper.toDTO(incidentRepository.save(incident));
    }

    public IncidentDTO resolveIncident(Long incidentId, String username, String resolution) {
        log.info("Resolving incident {} by user {}", incidentId, username);
        
        Incident incident = getIncidentEntity(incidentId);
        incident.setStatus("RESOLVED");
        incident.setResolvedAt(LocalDateTime.now());
        incident.setResolvedBy(username);
        incident.setResolution(resolution);
        
        return serviceMapper.toDTO(incidentRepository.save(incident));
    }

    public IncidentDTO closeIncident(Long incidentId) {
        log.info("Closing incident {}", incidentId);
        
        Incident incident = getIncidentEntity(incidentId);
        incident.setStatus("CLOSED");
        incident.setClosedAt(LocalDateTime.now());
        
        return serviceMapper.toDTO(incidentRepository.save(incident));
    }

    public IncidentDTO updateIncidentSeverity(Long incidentId, IncidentSeverity severity) {
        Incident incident = getIncidentEntity(incidentId);
        incident.setSeverity(severity);
        return serviceMapper.toDTO(incidentRepository.save(incident));
    }

    @Transactional(readOnly = true)
    public IncidentDTO getIncident(Long incidentId) {
        return serviceMapper.toDTO(getIncidentEntity(incidentId));
    }

    @Transactional(readOnly = true)
    public Page<IncidentDTO> getAllIncidents(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return incidentRepository.findAllByOrderByCreatedAtDesc(pageable)
                .map(serviceMapper::toDTO);
    }

    @Transactional(readOnly = true)
    public List<IncidentDTO> getActiveIncidents() {
        return incidentRepository.findActiveIncidents().stream()
                .map(serviceMapper::toDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<IncidentDTO> getActiveIncidentsByService(Long serviceId) {
        return incidentRepository.findActiveIncidentsByService(serviceId).stream()
                .map(serviceMapper::toDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<IncidentDTO> getIncidentsByService(Long serviceId) {
        return incidentRepository.findByServiceIdOrderByCreatedAtDesc(serviceId).stream()
                .map(serviceMapper::toDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Long countActiveIncidents() {
        return incidentRepository.countActiveIncidents();
    }

    @Transactional(readOnly = true)
    public Long countIncidentsSince(int hours) {
        LocalDateTime since = LocalDateTime.now().minusHours(hours);
        return incidentRepository.countIncidentsSince(since);
    }

    private Incident getIncidentEntity(Long id) {
        return incidentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Incident not found: " + id));
    }

    /**
     * Auto-resolve incidents when service becomes healthy
     */
    public void autoResolveIfHealthy(ManagedService service) {
        if (service.getHealthStatus() == HealthStatus.HEALTHY) {
            List<Incident> activeIncidents = incidentRepository.findActiveIncidentsByService(service.getId());
            
            for (Incident incident : activeIncidents) {
                if ("HEALTH_CHECK".equals(incident.getDetectionSource())) {
                    incident.setStatus("RESOLVED");
                    incident.setResolvedAt(LocalDateTime.now());
                    incident.setResolvedBy("SYSTEM");
                    incident.setResolution("Auto-resolved: Service returned to healthy state");
                    incidentRepository.save(incident);
                    
                    log.info("Auto-resolved incident {} for service {}", 
                            incident.getId(), service.getName());
                }
            }
        }
    }
}

