package com.management.console.service;

import com.management.console.domain.entity.ManagedService;
import com.management.console.exception.ResourceNotFoundException;
import com.management.console.repository.ManagedServiceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import jakarta.annotation.PostConstruct;
import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Service for tracking and analyzing application startup times and performance.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StartupAnalyticsService implements ApplicationListener<ApplicationReadyEvent> {

    private final ManagedServiceRepository serviceRepository;
    private final WebClient webClient;
    private final ApplicationContext applicationContext;

    // Store startup data for local application
    private StartupInfo localStartupInfo;
    
    // Store startup history for services
    private final Map<Long, List<StartupRecord>> startupHistory = new ConcurrentHashMap<>();
    private static final int MAX_HISTORY_PER_SERVICE = 50;

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        recordLocalStartup();
    }

    @PostConstruct
    public void init() {
        // Initialize startup tracking
        log.info("StartupAnalyticsService initialized");
    }

    private void recordLocalStartup() {
        long startTime = ManagementFactory.getRuntimeMXBean().getStartTime();
        long currentTime = System.currentTimeMillis();
        
        localStartupInfo = new StartupInfo();
        localStartupInfo.setStartTime(startTime);
        localStartupInfo.setReadyTime(currentTime);
        localStartupInfo.setTotalStartupTimeMs(currentTime - startTime);
        
        // Get bean initialization info
        String[] beanNames = applicationContext.getBeanDefinitionNames();
        localStartupInfo.setTotalBeans(beanNames.length);
        
        // Group beans by type
        Map<String, Integer> beansByType = new LinkedHashMap<>();
        for (String beanName : beanNames) {
            try {
                Object bean = applicationContext.getBean(beanName);
                String typeName = bean.getClass().getSimpleName();
                beansByType.merge(typeName, 1, Integer::sum);
            } catch (Exception e) {
                // Skip beans that can't be retrieved
            }
        }
        
        // Get top bean types
        localStartupInfo.setBeansByType(beansByType.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(20)
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (e1, e2) -> e1,
                        LinkedHashMap::new)));
        
        // JVM info
        localStartupInfo.setJvmName(ManagementFactory.getRuntimeMXBean().getVmName());
        localStartupInfo.setJvmVersion(ManagementFactory.getRuntimeMXBean().getVmVersion());
        
        log.info("Application startup completed in {}ms with {} beans", 
                localStartupInfo.getTotalStartupTimeMs(), localStartupInfo.getTotalBeans());
    }

    /**
     * Get local application startup info
     */
    public StartupInfo getLocalStartupInfo() {
        return localStartupInfo;
    }

    /**
     * Get startup info from remote service via actuator
     */
    public Mono<StartupInfo> getRemoteStartupInfo(Long serviceId) {
        ManagedService service = getService(serviceId);
        String actuatorUrl = buildActuatorUrl(service, "/startup");

        return webClient.get()
                .uri(actuatorUrl)
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(Duration.ofSeconds(15))
                .map(this::parseStartupResponse)
                .onErrorResume(e -> {
                    log.warn("Failed to fetch startup info from {}: {}", service.getName(), e.getMessage());
                    return Mono.just(new StartupInfo());
                });
    }

    /**
     * Record a service startup event
     */
    public void recordServiceStartup(Long serviceId, long startTimeMs, long readyTimeMs, String status) {
        StartupRecord record = new StartupRecord();
        record.setServiceId(serviceId);
        record.setStartTime(startTimeMs);
        record.setReadyTime(readyTimeMs);
        record.setDurationMs(readyTimeMs - startTimeMs);
        record.setStatus(status);
        record.setTimestamp(System.currentTimeMillis());

        startupHistory.computeIfAbsent(serviceId, k -> 
                Collections.synchronizedList(new ArrayList<>())).add(0, record);
        
        // Trim history
        List<StartupRecord> history = startupHistory.get(serviceId);
        while (history.size() > MAX_HISTORY_PER_SERVICE) {
            history.remove(history.size() - 1);
        }

        log.info("Recorded startup for service {}: {}ms", serviceId, record.getDurationMs());
    }

    /**
     * Get startup history for a service
     */
    public List<StartupRecord> getStartupHistory(Long serviceId) {
        return startupHistory.getOrDefault(serviceId, Collections.emptyList());
    }

    /**
     * Get startup statistics for a service
     */
    public StartupStats getStartupStats(Long serviceId) {
        List<StartupRecord> history = startupHistory.getOrDefault(serviceId, Collections.emptyList());
        
        StartupStats stats = new StartupStats();
        stats.setServiceId(serviceId);
        stats.setTotalStartups(history.size());

        if (!history.isEmpty()) {
            DoubleSummaryStatistics durationStats = history.stream()
                    .mapToDouble(StartupRecord::getDurationMs)
                    .summaryStatistics();
            
            stats.setAverageStartupTimeMs(durationStats.getAverage());
            stats.setMinStartupTimeMs(durationStats.getMin());
            stats.setMaxStartupTimeMs(durationStats.getMax());
            
            // Last startup
            stats.setLastStartupTimeMs(history.get(0).getDurationMs());
            stats.setLastStartupTimestamp(history.get(0).getTimestamp());
            
            // Success rate
            long successCount = history.stream()
                    .filter(r -> "SUCCESS".equalsIgnoreCase(r.getStatus()))
                    .count();
            stats.setSuccessRate((double) successCount / history.size() * 100);
            
            // Trend (compare last 5 vs previous 5)
            if (history.size() >= 10) {
                double recentAvg = history.subList(0, 5).stream()
                        .mapToDouble(StartupRecord::getDurationMs)
                        .average()
                        .orElse(0);
                double olderAvg = history.subList(5, 10).stream()
                        .mapToDouble(StartupRecord::getDurationMs)
                        .average()
                        .orElse(0);
                
                if (olderAvg > 0) {
                    double change = ((recentAvg - olderAvg) / olderAvg) * 100;
                    stats.setTrendPercentage(change);
                    stats.setTrend(change > 5 ? "DEGRADING" : change < -5 ? "IMPROVING" : "STABLE");
                }
            }
        }

        return stats;
    }

    /**
     * Get startup timeline for a service (detailed breakdown)
     */
    public Mono<StartupTimeline> getStartupTimeline(Long serviceId) {
        ManagedService service = getService(serviceId);
        String actuatorUrl = buildActuatorUrl(service, "/startup");

        return webClient.get()
                .uri(actuatorUrl)
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(Duration.ofSeconds(15))
                .map(this::parseStartupTimeline)
                .onErrorResume(e -> {
                    log.warn("Failed to fetch startup timeline from {}: {}", service.getName(), e.getMessage());
                    return Mono.just(new StartupTimeline());
                });
    }

    /**
     * Get aggregated startup metrics across all services
     */
    public AggregatedStartupMetrics getAggregatedMetrics() {
        AggregatedStartupMetrics metrics = new AggregatedStartupMetrics();
        metrics.setTimestamp(System.currentTimeMillis());
        
        List<StartupStats> allStats = startupHistory.keySet().stream()
                .map(this::getStartupStats)
                .filter(s -> s.getTotalStartups() > 0)
                .collect(Collectors.toList());
        
        if (!allStats.isEmpty()) {
            metrics.setTotalServicesTracked(allStats.size());
            
            double avgStartupTime = allStats.stream()
                    .mapToDouble(StartupStats::getAverageStartupTimeMs)
                    .average()
                    .orElse(0);
            metrics.setOverallAverageStartupMs(avgStartupTime);
            
            // Slowest services
            metrics.setSlowestServices(allStats.stream()
                    .sorted(Comparator.comparingDouble(StartupStats::getAverageStartupTimeMs).reversed())
                    .limit(5)
                    .map(s -> {
                        ManagedService svc = serviceRepository.findById(s.getServiceId()).orElse(null);
                        ServiceStartupSummary summary = new ServiceStartupSummary();
                        summary.setServiceId(s.getServiceId());
                        summary.setServiceName(svc != null ? svc.getName() : "Unknown");
                        summary.setAverageStartupMs(s.getAverageStartupTimeMs());
                        summary.setTrend(s.getTrend());
                        return summary;
                    })
                    .collect(Collectors.toList()));
            
            // Fastest services
            metrics.setFastestServices(allStats.stream()
                    .sorted(Comparator.comparingDouble(StartupStats::getAverageStartupTimeMs))
                    .limit(5)
                    .map(s -> {
                        ManagedService svc = serviceRepository.findById(s.getServiceId()).orElse(null);
                        ServiceStartupSummary summary = new ServiceStartupSummary();
                        summary.setServiceId(s.getServiceId());
                        summary.setServiceName(svc != null ? svc.getName() : "Unknown");
                        summary.setAverageStartupMs(s.getAverageStartupTimeMs());
                        summary.setTrend(s.getTrend());
                        return summary;
                    })
                    .collect(Collectors.toList()));
        }
        
        return metrics;
    }

    /**
     * Compare startup times between services
     */
    public StartupComparison compareStartups(List<Long> serviceIds) {
        StartupComparison comparison = new StartupComparison();
        comparison.setTimestamp(System.currentTimeMillis());
        
        List<ServiceStartupSummary> summaries = serviceIds.stream()
                .map(id -> {
                    StartupStats stats = getStartupStats(id);
                    ManagedService svc = serviceRepository.findById(id).orElse(null);
                    
                    ServiceStartupSummary summary = new ServiceStartupSummary();
                    summary.setServiceId(id);
                    summary.setServiceName(svc != null ? svc.getName() : "Unknown");
                    summary.setAverageStartupMs(stats.getAverageStartupTimeMs());
                    summary.setMinStartupMs(stats.getMinStartupTimeMs());
                    summary.setMaxStartupMs(stats.getMaxStartupTimeMs());
                    summary.setTrend(stats.getTrend());
                    summary.setTotalStartups(stats.getTotalStartups());
                    return summary;
                })
                .collect(Collectors.toList());
        
        comparison.setServices(summaries);
        
        // Calculate relative performance
        if (!summaries.isEmpty()) {
            double minAvg = summaries.stream()
                    .mapToDouble(ServiceStartupSummary::getAverageStartupMs)
                    .min()
                    .orElse(0);
            
            summaries.forEach(s -> {
                if (minAvg > 0) {
                    s.setRelativePerformance((s.getAverageStartupMs() / minAvg) * 100);
                }
            });
        }
        
        return comparison;
    }

    // Helper methods

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

    @SuppressWarnings("unchecked")
    private StartupInfo parseStartupResponse(Map<String, Object> response) {
        StartupInfo info = new StartupInfo();
        
        // Parse Spring Boot startup endpoint response
        Map<String, Object> timeline = (Map<String, Object>) response.get("timeline");
        if (timeline != null) {
            Number startTime = (Number) timeline.get("startTime");
            if (startTime != null) {
                info.setStartTime(startTime.longValue());
            }
            
            List<Map<String, Object>> events = (List<Map<String, Object>>) timeline.get("events");
            if (events != null && !events.isEmpty()) {
                Map<String, Object> lastEvent = events.get(events.size() - 1);
                Number endTime = (Number) lastEvent.get("endTime");
                if (endTime != null) {
                    info.setReadyTime(endTime.longValue());
                    info.setTotalStartupTimeMs(endTime.longValue() - info.getStartTime());
                }
            }
        }
        
        return info;
    }

    @SuppressWarnings("unchecked")
    private StartupTimeline parseStartupTimeline(Map<String, Object> response) {
        StartupTimeline timeline = new StartupTimeline();
        timeline.setTimestamp(System.currentTimeMillis());
        
        Map<String, Object> timelineData = (Map<String, Object>) response.get("timeline");
        if (timelineData != null) {
            Number startTime = (Number) timelineData.get("startTime");
            if (startTime != null) {
                timeline.setStartTime(startTime.longValue());
            }
            
            List<StartupStep> steps = new ArrayList<>();
            List<Map<String, Object>> events = (List<Map<String, Object>>) timelineData.get("events");
            if (events != null) {
                for (Map<String, Object> event : events) {
                    StartupStep step = new StartupStep();
                    step.setName((String) event.get("name"));
                    step.setId(String.valueOf(event.get("id")));
                    
                    Number start = (Number) event.get("startTime");
                    Number end = (Number) event.get("endTime");
                    if (start != null) step.setStartTimeMs(start.longValue());
                    if (end != null) step.setEndTimeMs(end.longValue());
                    if (start != null && end != null) {
                        step.setDurationMs(end.longValue() - start.longValue());
                    }
                    
                    steps.add(step);
                }
            }
            
            // Sort by duration (slowest first)
            steps.sort(Comparator.comparingLong(StartupStep::getDurationMs).reversed());
            timeline.setSteps(steps);
            
            // Top 10 slowest
            timeline.setSlowestSteps(steps.stream().limit(10).collect(Collectors.toList()));
        }
        
        return timeline;
    }

    // DTOs

    @lombok.Data
    public static class StartupInfo {
        private long startTime;
        private long readyTime;
        private long totalStartupTimeMs;
        private int totalBeans;
        private Map<String, Integer> beansByType;
        private String jvmName;
        private String jvmVersion;
    }

    @lombok.Data
    public static class StartupRecord {
        private Long serviceId;
        private long startTime;
        private long readyTime;
        private long durationMs;
        private String status;
        private long timestamp;
    }

    @lombok.Data
    public static class StartupStats {
        private Long serviceId;
        private int totalStartups;
        private double averageStartupTimeMs;
        private double minStartupTimeMs;
        private double maxStartupTimeMs;
        private double lastStartupTimeMs;
        private long lastStartupTimestamp;
        private double successRate;
        private String trend;
        private double trendPercentage;
    }

    @lombok.Data
    public static class StartupTimeline {
        private long timestamp;
        private long startTime;
        private List<StartupStep> steps;
        private List<StartupStep> slowestSteps;
    }

    @lombok.Data
    public static class StartupStep {
        private String id;
        private String name;
        private long startTimeMs;
        private long endTimeMs;
        private long durationMs;
    }

    @lombok.Data
    public static class AggregatedStartupMetrics {
        private long timestamp;
        private int totalServicesTracked;
        private double overallAverageStartupMs;
        private List<ServiceStartupSummary> slowestServices;
        private List<ServiceStartupSummary> fastestServices;
    }

    @lombok.Data
    public static class ServiceStartupSummary {
        private Long serviceId;
        private String serviceName;
        private double averageStartupMs;
        private double minStartupMs;
        private double maxStartupMs;
        private String trend;
        private int totalStartups;
        private double relativePerformance;
    }

    @lombok.Data
    public static class StartupComparison {
        private long timestamp;
        private List<ServiceStartupSummary> services;
    }
}

