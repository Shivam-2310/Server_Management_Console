package com.management.console.service;

import com.management.console.domain.entity.Incident;
import com.management.console.domain.entity.ManagedService;
import com.management.console.domain.enums.HealthStatus;
import com.management.console.domain.enums.IncidentSeverity;
import com.management.console.repository.IncidentRepository;
import com.management.console.repository.ManagedServiceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for generating wallboard/big-screen monitoring data.
 * Provides high-level overview suitable for display on large screens.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WallboardService {

    private final ManagedServiceRepository serviceRepository;
    private final IncidentRepository incidentRepository;
    private final JvmDiagnosticsService jvmDiagnosticsService;

    /**
     * Get comprehensive wallboard data
     */
    public WallboardData getWallboardData() {
        WallboardData data = new WallboardData();
        data.setTimestamp(System.currentTimeMillis());
        data.setLastUpdated(LocalDateTime.now());

        // Get all services
        List<ManagedService> services = serviceRepository.findAll();
        
        // Overall health summary
        data.setHealthSummary(calculateHealthSummary(services));
        
        // Service tiles (simplified view for each service)
        data.setServiceTiles(services.stream()
                .map(this::createServiceTile)
                .collect(Collectors.toList()));
        
        // Active incidents
        List<Incident> activeIncidents = incidentRepository.findActiveIncidents();
        data.setActiveIncidents(activeIncidents.stream()
                .map(this::createIncidentCard)
                .collect(Collectors.toList()));
        
        // Statistics
        data.setStats(calculateStats(services, activeIncidents));
        
        // Recent events (last few health changes, incidents, etc.)
        data.setRecentEvents(getRecentEvents());
        
        // System resources (local JVM)
        data.setSystemResources(getSystemResources());

        return data;
    }

    /**
     * Get simplified status grid (for minimal wallboard view)
     */
    public StatusGrid getStatusGrid() {
        List<ManagedService> services = serviceRepository.findAll();
        
        StatusGrid grid = new StatusGrid();
        grid.setTimestamp(System.currentTimeMillis());
        
        List<StatusCell> cells = new ArrayList<>();
        for (ManagedService service : services) {
            StatusCell cell = new StatusCell();
            cell.setServiceId(service.getId());
            cell.setServiceName(service.getName());
            cell.setStatus(service.getHealthStatus());
            cell.setStatusColor(getStatusColor(service.getHealthStatus()));
            cell.setEnvironment(service.getEnvironment());
            cells.add(cell);
        }
        
        grid.setCells(cells);
        grid.setTotalServices(cells.size());
        grid.setHealthyCount((int) cells.stream()
                .filter(c -> c.getStatus() == HealthStatus.HEALTHY)
                .count());
        grid.setUnhealthyCount((int) cells.stream()
                .filter(c -> c.getStatus() == HealthStatus.DOWN || c.getStatus() == HealthStatus.CRITICAL)
                .count());
        
        return grid;
    }

    /**
     * Get incident summary for wallboard
     */
    public IncidentSummary getIncidentSummary() {
        List<Incident> activeIncidents = incidentRepository.findActiveIncidents();
        
        IncidentSummary summary = new IncidentSummary();
        summary.setTimestamp(System.currentTimeMillis());
        summary.setTotalActive(activeIncidents.size());
        
        Map<IncidentSeverity, Long> bySeverity = activeIncidents.stream()
                .collect(Collectors.groupingBy(Incident::getSeverity, Collectors.counting()));
        
        summary.setCriticalCount(bySeverity.getOrDefault(IncidentSeverity.CRITICAL, 0L).intValue());
        summary.setHighCount(bySeverity.getOrDefault(IncidentSeverity.HIGH, 0L).intValue());
        summary.setMediumCount(bySeverity.getOrDefault(IncidentSeverity.MEDIUM, 0L).intValue());
        summary.setLowCount(bySeverity.getOrDefault(IncidentSeverity.LOW, 0L).intValue());
        
        // Most critical incident
        activeIncidents.stream()
                .filter(i -> i.getSeverity() == IncidentSeverity.CRITICAL)
                .findFirst()
                .ifPresent(incident -> {
                    summary.setMostCritical(createIncidentCard(incident));
                });
        
        return summary;
    }

    /**
     * Get performance overview for wallboard
     */
    public PerformanceOverview getPerformanceOverview() {
        PerformanceOverview overview = new PerformanceOverview();
        overview.setTimestamp(System.currentTimeMillis());
        
        // Local JVM metrics
        JvmDiagnosticsService.HeapInfo heapInfo = jvmDiagnosticsService.getLocalHeapInfo();
        if (heapInfo != null) {
            overview.setMemoryUsedPercent(
                    heapInfo.getHeapMax() > 0 ? 
                    (double) heapInfo.getHeapUsed() / heapInfo.getHeapMax() * 100 : 0);
            overview.setMemoryUsedMB(heapInfo.getHeapUsed() / (1024 * 1024));
            overview.setMemoryMaxMB(heapInfo.getHeapMax() / (1024 * 1024));
        }
        
        JvmDiagnosticsService.ThreadDumpInfo threadInfo = jvmDiagnosticsService.getLocalThreadDump();
        if (threadInfo != null) {
            overview.setActiveThreads(threadInfo.getTotalThreads());
            overview.setPeakThreads(threadInfo.getPeakThreadCount());
        }
        
        // CPU (approximate from system load)
        JvmDiagnosticsService.JvmInfo jvmInfo = jvmDiagnosticsService.getLocalJvmInfo();
        if (jvmInfo != null) {
            overview.setSystemLoad(jvmInfo.getSystemLoadAverage());
            overview.setAvailableProcessors(jvmInfo.getAvailableProcessors());
            overview.setUptimeMs(jvmInfo.getUptime());
        }
        
        return overview;
    }

    // Helper methods

    private HealthSummary calculateHealthSummary(List<ManagedService> services) {
        HealthSummary summary = new HealthSummary();
        summary.setTotalServices(services.size());
        
        Map<HealthStatus, Long> statusCounts = services.stream()
                .collect(Collectors.groupingBy(
                        s -> s.getHealthStatus() != null ? s.getHealthStatus() : HealthStatus.UNKNOWN,
                        Collectors.counting()));
        
        summary.setUpCount(statusCounts.getOrDefault(HealthStatus.HEALTHY, 0L).intValue());
        summary.setDownCount(statusCounts.getOrDefault(HealthStatus.DOWN, 0L).intValue() +
                            statusCounts.getOrDefault(HealthStatus.CRITICAL, 0L).intValue());
        summary.setDegradedCount(statusCounts.getOrDefault(HealthStatus.DEGRADED, 0L).intValue());
        summary.setUnknownCount(statusCounts.getOrDefault(HealthStatus.UNKNOWN, 0L).intValue());
        
        // Calculate overall status
        if (summary.getDownCount() > 0) {
            summary.setOverallStatus("CRITICAL");
            summary.setOverallColor("#FF0000");
        } else if (summary.getDegradedCount() > 0) {
            summary.setOverallStatus("WARNING");
            summary.setOverallColor("#FFA500");
        } else if (summary.getUnknownCount() == summary.getTotalServices()) {
            summary.setOverallStatus("UNKNOWN");
            summary.setOverallColor("#808080");
        } else {
            summary.setOverallStatus("HEALTHY");
            summary.setOverallColor("#00FF00");
        }
        
        // Health percentage
        summary.setHealthPercentage(summary.getTotalServices() > 0 ?
                (double) summary.getUpCount() / summary.getTotalServices() * 100 : 0);
        
        return summary;
    }

    private ServiceTile createServiceTile(ManagedService service) {
        ServiceTile tile = new ServiceTile();
        tile.setId(service.getId());
        tile.setName(service.getName());
        tile.setStatus(service.getHealthStatus());
        tile.setStatusColor(getStatusColor(service.getHealthStatus()));
        tile.setEnvironment(service.getEnvironment());
        tile.setServiceType(service.getServiceType().name());
        tile.setRunning(Boolean.TRUE.equals(service.getIsRunning()));
        tile.setLastChecked(service.getLastHealthCheck());
        return tile;
    }

    private IncidentCard createIncidentCard(Incident incident) {
        IncidentCard card = new IncidentCard();
        card.setId(incident.getId());
        card.setTitle(incident.getTitle());
        card.setSeverity(incident.getSeverity());
        card.setSeverityColor(getSeverityColor(incident.getSeverity()));
        card.setServiceName(incident.getService() != null ? incident.getService().getName() : "Unknown");
        card.setCreatedAt(incident.getCreatedAt());
        card.setDurationMinutes(java.time.Duration.between(incident.getCreatedAt(), LocalDateTime.now()).toMinutes());
        return card;
    }

    private WallboardStats calculateStats(List<ManagedService> services, List<Incident> activeIncidents) {
        WallboardStats stats = new WallboardStats();
        stats.setTotalServices(services.size());
        stats.setActiveIncidents(activeIncidents.size());
        stats.setBackendServices((int) services.stream()
                .filter(s -> s.getServiceType().name().contains("BACKEND"))
                .count());
        stats.setFrontendServices((int) services.stream()
                .filter(s -> s.getServiceType().name().contains("FRONTEND"))
                .count());
        
        // Environments breakdown
        Map<String, Long> byEnv = services.stream()
                .filter(s -> s.getEnvironment() != null)
                .collect(Collectors.groupingBy(ManagedService::getEnvironment, Collectors.counting()));
        stats.setServicesByEnvironment(byEnv);
        
        return stats;
    }

    private List<RecentEvent> getRecentEvents() {
        List<RecentEvent> events = new ArrayList<>();
        
        // In a real implementation, this would fetch from an event store
        // For now, return empty list or mock data
        
        return events;
    }

    private SystemResources getSystemResources() {
        SystemResources resources = new SystemResources();
        
        Runtime runtime = Runtime.getRuntime();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;
        long maxMemory = runtime.maxMemory();
        
        resources.setMemoryUsedMB(usedMemory / (1024 * 1024));
        resources.setMemoryTotalMB(totalMemory / (1024 * 1024));
        resources.setMemoryMaxMB(maxMemory / (1024 * 1024));
        resources.setMemoryUsedPercent((double) usedMemory / maxMemory * 100);
        resources.setAvailableProcessors(runtime.availableProcessors());
        
        return resources;
    }

    private String getStatusColor(HealthStatus status) {
        if (status == null) return "#808080";
        return switch (status) {
            case HEALTHY -> "#00FF00";
            case DEGRADED -> "#FFA500";
            case DOWN, CRITICAL -> "#FF0000";
            case UNKNOWN -> "#808080";
        };
    }

    private String getSeverityColor(IncidentSeverity severity) {
        return switch (severity) {
            case CRITICAL -> "#FF0000";
            case HIGH -> "#FFA500";
            case MEDIUM -> "#FFFF00";
            case LOW -> "#00BFFF";
        };
    }

    // DTOs

    @lombok.Data
    public static class WallboardData {
        private long timestamp;
        private LocalDateTime lastUpdated;
        private HealthSummary healthSummary;
        private List<ServiceTile> serviceTiles;
        private List<IncidentCard> activeIncidents;
        private WallboardStats stats;
        private List<RecentEvent> recentEvents;
        private SystemResources systemResources;
    }

    @lombok.Data
    public static class HealthSummary {
        private int totalServices;
        private int upCount;
        private int downCount;
        private int degradedCount;
        private int unknownCount;
        private String overallStatus;
        private String overallColor;
        private double healthPercentage;
    }

    @lombok.Data
    public static class ServiceTile {
        private Long id;
        private String name;
        private HealthStatus status;
        private String statusColor;
        private String environment;
        private String serviceType;
        private boolean running;
        private LocalDateTime lastChecked;
    }

    @lombok.Data
    public static class IncidentCard {
        private Long id;
        private String title;
        private IncidentSeverity severity;
        private String severityColor;
        private String serviceName;
        private LocalDateTime createdAt;
        private long durationMinutes;
    }

    @lombok.Data
    public static class WallboardStats {
        private int totalServices;
        private int activeIncidents;
        private int backendServices;
        private int frontendServices;
        private Map<String, Long> servicesByEnvironment;
    }

    @lombok.Data
    public static class RecentEvent {
        private String type;
        private String message;
        private String serviceName;
        private LocalDateTime timestamp;
        private String severity;
    }

    @lombok.Data
    public static class SystemResources {
        private long memoryUsedMB;
        private long memoryTotalMB;
        private long memoryMaxMB;
        private double memoryUsedPercent;
        private int availableProcessors;
    }

    @lombok.Data
    public static class StatusGrid {
        private long timestamp;
        private int totalServices;
        private int healthyCount;
        private int unhealthyCount;
        private List<StatusCell> cells;
    }

    @lombok.Data
    public static class StatusCell {
        private Long serviceId;
        private String serviceName;
        private HealthStatus status;
        private String statusColor;
        private String environment;
    }

    @lombok.Data
    public static class IncidentSummary {
        private long timestamp;
        private int totalActive;
        private int criticalCount;
        private int highCount;
        private int mediumCount;
        private int lowCount;
        private IncidentCard mostCritical;
    }

    @lombok.Data
    public static class PerformanceOverview {
        private long timestamp;
        private double memoryUsedPercent;
        private long memoryUsedMB;
        private long memoryMaxMB;
        private int activeThreads;
        private int peakThreads;
        private double systemLoad;
        private int availableProcessors;
        private long uptimeMs;
    }
}

