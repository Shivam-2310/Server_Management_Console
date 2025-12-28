package com.management.console.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.management.console.domain.entity.ManagedService;
import com.management.console.domain.entity.ServiceMetrics;
import com.management.console.domain.enums.ServiceType;
import com.management.console.dto.MetricsDTO;
import com.management.console.mapper.ServiceMapper;
import com.management.console.repository.ManagedServiceRepository;
import com.management.console.repository.ServiceMetricsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MetricsCollectorService {

    private final ManagedServiceRepository serviceRepository;
    private final ServiceMetricsRepository metricsRepository;
    private final ServiceMapper serviceMapper;
    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;

    @Transactional
    public MetricsDTO collectMetrics(Long serviceId) {
        ManagedService service = serviceRepository.findById(serviceId)
                .orElseThrow(() -> new RuntimeException("Service not found: " + serviceId));
        
        return collectMetrics(service);
    }

    @Transactional
    public MetricsDTO collectMetrics(ManagedService service) {
        log.debug("Collecting metrics for service: {}", service.getName());
        
        ServiceMetrics metrics = ServiceMetrics.builder()
                .service(service)
                .timestamp(LocalDateTime.now())
                .build();

        try {
            if (service.getServiceType() == ServiceType.BACKEND) {
                collectBackendMetrics(service, metrics);
            } else {
                collectFrontendMetrics(service, metrics);
            }
        } catch (Exception e) {
            log.error("Metrics collection failed for service {}: {}", service.getName(), e.getMessage());
        }

        // Save metrics
        ServiceMetrics saved = metricsRepository.save(metrics);
        
        // Update service snapshot
        updateServiceMetricsSnapshot(service, metrics);
        
        return serviceMapper.toDTO(saved);
    }

    private void collectBackendMetrics(ManagedService service, ServiceMetrics metrics) {
        String actuatorBase = service.getActuatorUrl();
        WebClient webClient = webClientBuilder.build();

        // Collect JVM metrics
        try {
            collectJvmMetrics(webClient, actuatorBase + "/metrics", metrics);
        } catch (Exception e) {
            log.warn("Failed to collect JVM metrics for {}: {}", service.getName(), e.getMessage());
        }

        // Collect HTTP metrics
        try {
            collectHttpMetrics(webClient, actuatorBase + "/metrics", metrics);
        } catch (Exception e) {
            log.warn("Failed to collect HTTP metrics for {}: {}", service.getName(), e.getMessage());
        }
    }

    private void collectJvmMetrics(WebClient webClient, String metricsBase, ServiceMetrics metrics) {
        // CPU usage
        Double cpuUsage = getMetricValue(webClient, metricsBase + "/process.cpu.usage");
        if (cpuUsage != null) {
            metrics.setCpuUsage(cpuUsage * 100); // Convert to percentage
        }

        Double systemCpu = getMetricValue(webClient, metricsBase + "/system.cpu.usage");
        if (systemCpu != null) {
            metrics.setSystemCpuUsage(systemCpu * 100);
        }

        // Memory - JVM heap
        Double heapUsed = getMetricValue(webClient, metricsBase + "/jvm.memory.used", "area", "heap");
        Double heapMax = getMetricValue(webClient, metricsBase + "/jvm.memory.max", "area", "heap");
        
        if (heapUsed != null) {
            metrics.setHeapUsed(heapUsed.longValue());
        }
        if (heapMax != null) {
            metrics.setHeapMax(heapMax.longValue());
        }
        if (heapUsed != null && heapMax != null && heapMax > 0) {
            metrics.setMemoryUsagePercent((heapUsed / heapMax) * 100);
            metrics.setMemoryUsed(heapUsed.longValue());
            metrics.setMemoryMax(heapMax.longValue());
        }

        // Non-heap memory
        Double nonHeapUsed = getMetricValue(webClient, metricsBase + "/jvm.memory.used", "area", "nonheap");
        if (nonHeapUsed != null) {
            metrics.setNonHeapUsed(nonHeapUsed.longValue());
        }

        // Thread metrics
        Double threadCount = getMetricValue(webClient, metricsBase + "/jvm.threads.live");
        Double peakThreads = getMetricValue(webClient, metricsBase + "/jvm.threads.peak");
        Double daemonThreads = getMetricValue(webClient, metricsBase + "/jvm.threads.daemon");
        
        if (threadCount != null) metrics.setThreadCount(threadCount.intValue());
        if (peakThreads != null) metrics.setThreadPeakCount(peakThreads.intValue());
        if (daemonThreads != null) metrics.setThreadDaemonCount(daemonThreads.intValue());

        // GC metrics
        Double gcPauseCount = getMetricValue(webClient, metricsBase + "/jvm.gc.pause", "count");
        Double gcPauseTime = getMetricValue(webClient, metricsBase + "/jvm.gc.pause", "totalTime");
        
        if (gcPauseCount != null) metrics.setGcPauseCount(gcPauseCount.longValue());
        if (gcPauseTime != null) metrics.setGcPauseTime(gcPauseTime);

        // Uptime
        Double uptime = getMetricValue(webClient, metricsBase + "/process.uptime");
        if (uptime != null) {
            metrics.setUptimeSeconds(uptime.longValue());
        }
    }

    private void collectHttpMetrics(WebClient webClient, String metricsBase, ServiceMetrics metrics) {
        // HTTP request count
        Double httpCount = getMetricValue(webClient, metricsBase + "/http.server.requests", "count");
        if (httpCount != null) {
            metrics.setHttpRequestsTotal(httpCount.longValue());
        }

        // HTTP request timing
        Double httpTotalTime = getMetricValue(webClient, metricsBase + "/http.server.requests", "totalTime");
        if (httpCount != null && httpTotalTime != null && httpCount > 0) {
            metrics.setAverageResponseTime((httpTotalTime / httpCount) * 1000); // Convert to ms
        }

        // Error counts (5xx responses) - simplified without status filtering for MVP
        Double http5xx = getMetricValue(webClient, metricsBase + "/http.server.requests?tag=status:5xx", "count");
        if (http5xx != null) {
            metrics.setHttp5xxCount(http5xx.longValue());
        }

        // 4xx responses
        Double http4xx = getMetricValue(webClient, metricsBase + "/http.server.requests?tag=status:4xx", "count");
        if (http4xx != null) {
            metrics.setHttp4xxCount(http4xx.longValue());
        }

        // Calculate error rate
        if (httpCount != null && httpCount > 0 && http5xx != null) {
            metrics.setErrorRate((http5xx / httpCount) * 100);
            metrics.setErrorCount(http5xx.longValue());
        }
    }

    private void collectFrontendMetrics(ManagedService service, ServiceMetrics metrics) {
        // For frontend services, we do synthetic checks
        WebClient webClient = webClientBuilder.build();
        
        try {
            long startTime = System.currentTimeMillis();
            
            ResponseEntity<String> response = webClient.get()
                    .uri(service.getFullUrl())
                    .retrieve()
                    .toEntity(String.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();

            long loadTime = System.currentTimeMillis() - startTime;
            metrics.setPageLoadTime((double) loadTime);
            
            if (response != null && response.getBody() != null) {
                // Approximate bundle size from response
                metrics.setBundleSize((long) response.getBody().length());
                metrics.setAssetsAvailable(true);
            }
        } catch (Exception e) {
            log.warn("Frontend metrics collection failed for {}: {}", service.getName(), e.getMessage());
            metrics.setAssetsAvailable(false);
        }
    }

    private Double getMetricValue(WebClient webClient, String url) {
        try {
            ResponseEntity<String> response = webClient.get()
                    .uri(url)
                    .retrieve()
                    .toEntity(String.class)
                    .timeout(Duration.ofSeconds(5))
                    .block();

            if (response != null && response.getBody() != null) {
                JsonNode json = objectMapper.readTree(response.getBody());
                JsonNode measurements = json.path("measurements");
                if (measurements.isArray() && measurements.size() > 0) {
                    return measurements.get(0).path("value").asDouble();
                }
            }
        } catch (Exception e) {
            log.trace("Failed to get metric from {}: {}", url, e.getMessage());
        }
        return null;
    }

    private Double getMetricValue(WebClient webClient, String url, String statistic) {
        try {
            ResponseEntity<String> response = webClient.get()
                    .uri(url)
                    .retrieve()
                    .toEntity(String.class)
                    .timeout(Duration.ofSeconds(5))
                    .block();

            if (response != null && response.getBody() != null) {
                JsonNode json = objectMapper.readTree(response.getBody());
                JsonNode measurements = json.path("measurements");
                if (measurements.isArray()) {
                    for (JsonNode measurement : measurements) {
                        if (statistic.equals(measurement.path("statistic").asText())) {
                            return measurement.path("value").asDouble();
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.trace("Failed to get metric from {}: {}", url, e.getMessage());
        }
        return null;
    }

    private Double getMetricValue(WebClient webClient, String url, String tagKey, String tagValuePrefix) {
        try {
            String fullUrl = url + "?tag=" + tagKey + ":" + tagValuePrefix;
            return getMetricValue(webClient, fullUrl);
        } catch (Exception e) {
            log.trace("Failed to get tagged metric: {}", e.getMessage());
        }
        return null;
    }

    @Transactional
    public void updateServiceMetricsSnapshot(ManagedService service, ServiceMetrics metrics) {
        service.setCpuUsage(metrics.getCpuUsage());
        service.setMemoryUsage(metrics.getMemoryUsagePercent());
        service.setResponseTime(metrics.getAverageResponseTime() != null ? 
                metrics.getAverageResponseTime().longValue() : null);
        service.setErrorRate(metrics.getErrorRate());
        service.setLastMetricsCollection(LocalDateTime.now());
        serviceRepository.save(service);
    }

    @Async
    public CompletableFuture<List<MetricsDTO>> collectMetricsForAll() {
        log.info("Collecting metrics for all enabled services");
        
        List<ManagedService> services = serviceRepository.findByEnabledTrue();
        
        List<MetricsDTO> results = services.stream()
                .map(this::collectMetrics)
                .collect(Collectors.toList());
        
        return CompletableFuture.completedFuture(results);
    }

    @Transactional(readOnly = true)
    public List<MetricsDTO> getRecentMetrics(Long serviceId, int hours) {
        LocalDateTime since = LocalDateTime.now().minusHours(hours);
        return metricsRepository.findRecentMetrics(serviceId, since).stream()
                .map(serviceMapper::toDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public MetricsDTO getLatestMetrics(Long serviceId) {
        return metricsRepository.findTopByServiceIdOrderByTimestampDesc(serviceId)
                .map(serviceMapper::toDTO)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public Double getAverageCpuUsage(Long serviceId, int hours) {
        LocalDateTime since = LocalDateTime.now().minusHours(hours);
        return metricsRepository.getAverageCpuUsage(serviceId, since);
    }

    @Transactional(readOnly = true)
    public Double getAverageMemoryUsage(Long serviceId, int hours) {
        LocalDateTime since = LocalDateTime.now().minusHours(hours);
        return metricsRepository.getAverageMemoryUsage(serviceId, since);
    }

    @Transactional(readOnly = true)
    public Double getAverageErrorRate(Long serviceId, int hours) {
        LocalDateTime since = LocalDateTime.now().minusHours(hours);
        return metricsRepository.getAverageErrorRate(serviceId, since);
    }

    @Transactional
    public void cleanupOldMetrics(int retentionDays) {
        LocalDateTime threshold = LocalDateTime.now().minusDays(retentionDays);
        int deleted = metricsRepository.deleteOldMetrics(threshold);
        log.info("Cleaned up {} old metrics records", deleted);
    }
}

