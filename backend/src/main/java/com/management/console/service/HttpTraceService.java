package com.management.console.service;

import com.management.console.domain.entity.ManagedService;
import com.management.console.exception.ResourceNotFoundException;
import com.management.console.repository.ManagedServiceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.stream.Collectors;

/**
 * Service for HTTP request tracing and performance monitoring.
 * Tracks request/response details for analysis.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class HttpTraceService {

    private final ManagedServiceRepository serviceRepository;
    private final WebClient webClient;

    // In-memory trace storage (circular buffer)
    private final Deque<HttpTrace> traces = new ConcurrentLinkedDeque<>();
    private static final int MAX_TRACES = 500;

    // Performance statistics
    private final Map<String, EndpointStats> endpointStats = new LinkedHashMap<>();

    /**
     * Record an HTTP trace
     */
    public void recordTrace(HttpTrace trace) {
        traces.addFirst(trace);
        while (traces.size() > MAX_TRACES) {
            traces.removeLast();
        }

        // Update endpoint statistics
        String key = trace.getMethod() + " " + normalizeUri(trace.getUri());
        endpointStats.compute(key, (k, stats) -> {
            if (stats == null) {
                stats = new EndpointStats();
                stats.setEndpoint(k);
            }
            stats.recordRequest(trace.getTimeTaken(), trace.getStatus());
            return stats;
        });
    }

    /**
     * Get recent traces
     */
    public List<HttpTrace> getRecentTraces(int limit) {
        return traces.stream()
                .limit(limit)
                .collect(Collectors.toList());
    }

    /**
     * Get traces filtered by various criteria
     */
    public List<HttpTrace> getFilteredTraces(TraceFilter filter) {
        return traces.stream()
                .filter(trace -> matchesFilter(trace, filter))
                .limit(filter.getLimit() != null ? filter.getLimit() : 100)
                .collect(Collectors.toList());
    }

    /**
     * Get traces from a remote service via actuator
     */
    @SuppressWarnings("unchecked")
    public Mono<List<HttpTrace>> getRemoteTraces(Long serviceId, int limit) {
        ManagedService service = getService(serviceId);
        String actuatorUrl = buildActuatorUrl(service, "/httptrace");

        return webClient.get()
                .uri(actuatorUrl)
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(Duration.ofSeconds(10))
                .map(response -> parseHttpTraces((Map<String, Object>) response, limit))
                .onErrorResume(e -> {
                    log.warn("Failed to fetch HTTP traces from {}: {}", service.getName(), e.getMessage());
                    return Mono.just(new ArrayList<>());
                });
    }

    /**
     * Get endpoint statistics
     */
    public List<EndpointStats> getEndpointStats() {
        return new ArrayList<>(endpointStats.values()).stream()
                .sorted(Comparator.comparingLong(EndpointStats::getTotalRequests).reversed())
                .collect(Collectors.toList());
    }

    /**
     * Get endpoint statistics sorted by average response time
     */
    public List<EndpointStats> getSlowestEndpoints(int limit) {
        return endpointStats.values().stream()
                .sorted(Comparator.comparingDouble(EndpointStats::getAverageTime).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    /**
     * Get overall performance summary
     */
    public PerformanceSummary getPerformanceSummary() {
        PerformanceSummary summary = new PerformanceSummary();
        summary.setTotalRequests(traces.size());

        if (!traces.isEmpty()) {
            DoubleSummaryStatistics timeStats = traces.stream()
                    .mapToDouble(HttpTrace::getTimeTaken)
                    .summaryStatistics();
            
            summary.setAverageResponseTime(timeStats.getAverage());
            summary.setMinResponseTime(timeStats.getMin());
            summary.setMaxResponseTime(timeStats.getMax());

            // Status code distribution
            Map<Integer, Long> statusDistribution = traces.stream()
                    .collect(Collectors.groupingBy(HttpTrace::getStatus, Collectors.counting()));
            summary.setStatusCodeDistribution(statusDistribution);

            // Method distribution
            Map<String, Long> methodDistribution = traces.stream()
                    .collect(Collectors.groupingBy(HttpTrace::getMethod, Collectors.counting()));
            summary.setMethodDistribution(methodDistribution);

            // Calculate percentiles
            List<Double> sortedTimes = traces.stream()
                    .map(HttpTrace::getTimeTaken)
                    .sorted()
                    .collect(Collectors.toList());
            
            summary.setP50ResponseTime(percentile(sortedTimes, 50));
            summary.setP90ResponseTime(percentile(sortedTimes, 90));
            summary.setP99ResponseTime(percentile(sortedTimes, 99));

            // Error rate
            long errorCount = traces.stream()
                    .filter(t -> t.getStatus() >= 400)
                    .count();
            summary.setErrorRate((double) errorCount / traces.size() * 100);
        }

        return summary;
    }

    /**
     * Get traces with errors (4xx, 5xx)
     */
    public List<HttpTrace> getErrorTraces(int limit) {
        return traces.stream()
                .filter(t -> t.getStatus() >= 400)
                .limit(limit)
                .collect(Collectors.toList());
    }

    /**
     * Get slow requests (above threshold)
     */
    public List<HttpTrace> getSlowRequests(double thresholdMs, int limit) {
        return traces.stream()
                .filter(t -> t.getTimeTaken() > thresholdMs)
                .limit(limit)
                .collect(Collectors.toList());
    }

    /**
     * Clear trace history
     */
    public void clearTraces() {
        traces.clear();
        log.info("HTTP traces cleared");
    }

    /**
     * Clear endpoint statistics
     */
    public void clearStats() {
        endpointStats.clear();
        log.info("Endpoint statistics cleared");
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

    private boolean matchesFilter(HttpTrace trace, TraceFilter filter) {
        if (filter.getMethod() != null && !filter.getMethod().equalsIgnoreCase(trace.getMethod())) {
            return false;
        }
        if (filter.getUriPattern() != null && !trace.getUri().contains(filter.getUriPattern())) {
            return false;
        }
        if (filter.getMinStatus() != null && trace.getStatus() < filter.getMinStatus()) {
            return false;
        }
        if (filter.getMaxStatus() != null && trace.getStatus() > filter.getMaxStatus()) {
            return false;
        }
        if (filter.getMinTime() != null && trace.getTimeTaken() < filter.getMinTime()) {
            return false;
        }
        if (filter.getSince() != null && trace.getTimestamp() < filter.getSince()) {
            return false;
        }
        return true;
    }

    private String normalizeUri(String uri) {
        // Replace path parameters with placeholders
        return uri.replaceAll("/\\d+", "/{id}")
                  .replaceAll("/[a-f0-9-]{36}", "/{uuid}");
    }

    private double percentile(List<Double> sortedValues, int percentile) {
        if (sortedValues.isEmpty()) return 0;
        int index = (int) Math.ceil(percentile / 100.0 * sortedValues.size()) - 1;
        return sortedValues.get(Math.max(0, Math.min(index, sortedValues.size() - 1)));
    }

    @SuppressWarnings("unchecked")
    private List<HttpTrace> parseHttpTraces(Map<String, Object> response, int limit) {
        List<HttpTrace> result = new ArrayList<>();
        
        List<Map<String, Object>> traces = (List<Map<String, Object>>) response.get("traces");
        if (traces != null) {
            for (Map<String, Object> trace : traces) {
                if (result.size() >= limit) break;
                
                HttpTrace httpTrace = new HttpTrace();
                
                Map<String, Object> request = (Map<String, Object>) trace.get("request");
                if (request != null) {
                    httpTrace.setMethod((String) request.get("method"));
                    httpTrace.setUri((String) request.get("uri"));
                    httpTrace.setRequestHeaders((Map<String, List<String>>) request.get("headers"));
                }
                
                Map<String, Object> responsePart = (Map<String, Object>) trace.get("response");
                if (responsePart != null) {
                    httpTrace.setStatus((Integer) responsePart.get("status"));
                    httpTrace.setResponseHeaders((Map<String, List<String>>) responsePart.get("headers"));
                }
                
                Number timeTaken = (Number) trace.get("timeTaken");
                if (timeTaken != null) {
                    httpTrace.setTimeTaken(timeTaken.doubleValue());
                }
                
                String timestamp = (String) trace.get("timestamp");
                if (timestamp != null) {
                    httpTrace.setTimestamp(Instant.parse(timestamp).toEpochMilli());
                }
                
                result.add(httpTrace);
            }
        }
        
        return result;
    }

    // DTOs

    @lombok.Data
    public static class HttpTrace {
        private long timestamp;
        private String method;
        private String uri;
        private int status;
        private double timeTaken; // in milliseconds
        private Map<String, List<String>> requestHeaders;
        private Map<String, List<String>> responseHeaders;
        private String remoteAddress;
        private String principal;
        private String sessionId;
    }

    @lombok.Data
    public static class TraceFilter {
        private String method;
        private String uriPattern;
        private Integer minStatus;
        private Integer maxStatus;
        private Double minTime;
        private Long since;
        private Integer limit;
    }

    @lombok.Data
    public static class EndpointStats {
        private String endpoint;
        private long totalRequests;
        private long successCount;
        private long errorCount;
        private double averageTime;
        private double minTime = Double.MAX_VALUE;
        private double maxTime;
        private double totalTime;

        public void recordRequest(double timeTaken, int status) {
            totalRequests++;
            totalTime += timeTaken;
            averageTime = totalTime / totalRequests;
            minTime = Math.min(minTime, timeTaken);
            maxTime = Math.max(maxTime, timeTaken);
            
            if (status >= 400) {
                errorCount++;
            } else {
                successCount++;
            }
        }
    }

    @lombok.Data
    public static class PerformanceSummary {
        private long totalRequests;
        private double averageResponseTime;
        private double minResponseTime;
        private double maxResponseTime;
        private double p50ResponseTime;
        private double p90ResponseTime;
        private double p99ResponseTime;
        private double errorRate;
        private Map<Integer, Long> statusCodeDistribution;
        private Map<String, Long> methodDistribution;
    }
}

