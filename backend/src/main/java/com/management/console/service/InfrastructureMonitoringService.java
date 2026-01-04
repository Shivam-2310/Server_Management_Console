package com.management.console.service;

import com.management.console.domain.entity.ManagedService;
import com.management.console.exception.ResourceNotFoundException;
import com.management.console.repository.ManagedServiceRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.RuntimeMXBean;
import java.time.Duration;
import java.util.*;

/**
 * Service for collecting infrastructure-level information from services.
 * Includes server specifications, OS details, and JVM information.
 * Follows Spring Boot Admin's approach to fetch from multiple actuator endpoints.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InfrastructureMonitoringService {

    private final ManagedServiceRepository serviceRepository;
    private final WebClient.Builder webClientBuilder;

    /**
     * Get infrastructure information from a remote service via Actuator.
     * Fetches from multiple endpoints: /metrics, /info
     */
    public Mono<InfrastructureInfo> getRemoteInfrastructureInfo(Long serviceId) {
        ManagedService service = getService(serviceId);
        
        if (service.getServiceType() != com.management.console.domain.enums.ServiceType.BACKEND) {
            log.warn("Infrastructure monitoring only supported for BACKEND services");
            return Mono.just(createEmptyInfrastructureInfo(service));
        }

        // Validate service configuration
        if (service.getHost() == null || service.getPort() == null) {
            log.error("Service {} is missing host or port configuration", service.getName());
            return Mono.just(createEmptyInfrastructureInfo(service));
        }

        // Set explicit buffer limit for metrics responses
        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(-1)) // Unlimited - maximum memory
                .build();

        // Build WebClient without baseUrl to allow full URLs
        WebClient webClient = WebClient.builder()
                .exchangeStrategies(strategies) // Explicitly set buffer limit
                .build();
        String baseUrl = buildActuatorBaseUrl(service);

        log.info("=== STEP 1: Fetching infrastructure info ===");
        log.info("Service: {} (ID: {})", service.getName(), service.getId());
        log.info("Host: {}, Port: {}", service.getHost(), service.getPort());
        log.info("Base URL: {}", baseUrl);

        // Fetch metrics and info in parallel
        Mono<Map<String, Object>> metricsMono = webClient.get()
                .uri(baseUrl + "/metrics")
                .retrieve()
                .bodyToMono(Map.class)
                .cast(Map.class)
                .map(m -> (Map<String, Object>) m)
                .timeout(Duration.ofSeconds(15))
                .doOnNext(metrics -> {
                    log.info("=== STEP 3: Received metrics from {} ===", service.getName());
                    log.info("Metrics size: {} keys", metrics != null ? metrics.size() : 0);
                    if (metrics != null && !metrics.isEmpty()) {
                        log.info("Metrics keys: {}", metrics.keySet());
                    } else {
                        log.warn("WARNING: Empty metrics response from {}", service.getName());
                    }
                })
                .doOnError(error -> {
                    log.error("=== ERROR fetching metrics from {} ===", service.getName());
                    log.error("URL: {}", baseUrl + "/metrics");
                    log.error("Error: {}", error.getMessage());
                    log.error("Full stack trace:", error);
                })
                .onErrorResume(e -> {
                    log.warn("Failed to fetch metrics from {}: {}", service.getName(), e.getMessage());
                    return Mono.just(new HashMap<>());
                });

        Mono<Map<String, Object>> infoMono = webClient.get()
                .uri(baseUrl + "/info")
                .retrieve()
                .bodyToMono(Map.class)
                .cast(Map.class)
                .map(m -> (Map<String, Object>) m)
                .timeout(Duration.ofSeconds(15))
                .doOnNext(info -> {
                    log.info("=== STEP 3: Received info from {} ===", service.getName());
                    log.info("Info size: {} keys", info != null ? info.size() : 0);
                    if (info != null && !info.isEmpty()) {
                        log.info("Info keys: {}", info.keySet());
                    } else {
                        log.warn("WARNING: Empty info response from {}", service.getName());
                    }
                })
                .doOnError(error -> {
                    log.error("=== ERROR fetching info from {} ===", service.getName());
                    log.error("URL: {}", baseUrl + "/info");
                    log.error("Error: {}", error.getMessage());
                    log.error("Full stack trace:", error);
                })
                .onErrorResume(e -> {
                    log.warn("Failed to fetch info from {}: {}", service.getName(), e.getMessage());
                    return Mono.just(new HashMap<>());
                });

        // Fetch specific system metrics
        Mono<Map<String, Object>> systemCpuMono = fetchMetric(webClient, baseUrl, "system.cpu.usage");
        Mono<Map<String, Object>> processCpuMono = fetchMetric(webClient, baseUrl, "process.cpu.usage");
        Mono<Map<String, Object>> systemLoadMono = fetchMetric(webClient, baseUrl, "system.load.average.1m");
        Mono<Map<String, Object>> processUptimeMono = fetchMetric(webClient, baseUrl, "process.uptime");

        return Mono.zip(metricsMono, infoMono, systemCpuMono, processCpuMono, systemLoadMono, processUptimeMono)
                .map(tuple -> {
                    log.info("=== STEP 4: Parsing infrastructure info ===");
                    Map<String, Object> allMetrics = tuple.getT1();
                    Map<String, Object> info = tuple.getT2();
                    Map<String, Object> systemCpu = tuple.getT3();
                    Map<String, Object> processCpu = tuple.getT4();
                    Map<String, Object> systemLoad = tuple.getT5();
                    Map<String, Object> processUptime = tuple.getT6();
                    
                    log.info("Metrics available: {}, Info available: {}", 
                            allMetrics != null && !allMetrics.isEmpty(),
                            info != null && !info.isEmpty());
                    
                    InfrastructureInfo infraInfo = parseInfrastructureInfo(service, allMetrics, info, 
                            systemCpu, processCpu, systemLoad, processUptime);
                    log.info("=== STEP 5: Infrastructure info parsed ===");
                    log.info("Parsed infrastructure info from {}: OS={}, JVM={}, CPU={}%", 
                            service.getName(), 
                            infraInfo.getOsName(),
                            infraInfo.getJvmName(),
                            infraInfo.getSystemCpuLoad());
                    return infraInfo;
                })
                .doOnError(error -> {
                    log.error("=== ERROR parsing infrastructure info ===");
                    log.error("Service: {} (ID: {})", service.getName(), service.getId());
                    log.error("Error: {}", error.getMessage());
                    log.error("Full stack trace:", error);
                })
                .onErrorResume(e -> {
                    log.error("Failed to fetch infrastructure info from {}: {}", service.getName(), e.getMessage(), e);
                    return Mono.just(createEmptyInfrastructureInfo(service));
                });
    }

    /**
     * Get JVM information from a remote service.
     * Fetches specific JVM metrics from /actuator/metrics
     */
    public Mono<JvmInfo> getRemoteJvmInfo(Long serviceId) {
        ManagedService service = getService(serviceId);
        
        log.info("=== STEP 1: Fetching JVM info ===");
        log.info("Service: {} (ID: {})", service.getName(), service.getId());
        log.info("Host: {}, Port: {}", service.getHost(), service.getPort());
        
        // Set explicit buffer limit for metrics responses
        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(-1)) // Unlimited - maximum memory
                .build();

        // Build WebClient without baseUrl to allow full URLs
        WebClient webClient = WebClient.builder()
                .exchangeStrategies(strategies) // Explicitly set buffer limit
                .build();
        String baseUrl = buildActuatorBaseUrl(service);
        log.info("Actuator Base URL: {}", baseUrl);

        // Fetch all metrics first
        Mono<Map<String, Object>> allMetricsMono = webClient.get()
                .uri(baseUrl + "/metrics")
                .retrieve()
                .bodyToMono(Map.class)
                .cast(Map.class)
                .map(m -> (Map<String, Object>) m)
                .timeout(Duration.ofSeconds(15))
                .onErrorResume(e -> {
                    log.debug("Failed to fetch metrics from {}: {}", service.getName(), e.getMessage());
                    return Mono.just(new HashMap<>());
                });

        // Fetch specific JVM metrics in parallel
        // For heap memory, we need to filter by area=heap
        Mono<Map<String, Object>> jvmMemoryUsedMono = fetchMetric(webClient, baseUrl, "jvm.memory.used?tag=area:heap");
        Mono<Map<String, Object>> jvmMemoryMaxMono = fetchMetric(webClient, baseUrl, "jvm.memory.max?tag=area:heap");
        Mono<Map<String, Object>> jvmMemoryCommittedMono = fetchMetric(webClient, baseUrl, "jvm.memory.committed?tag=area:heap");
        Mono<Map<String, Object>> jvmNonHeapUsedMono = fetchMetric(webClient, baseUrl, "jvm.memory.used?tag=area:nonheap");
        Mono<Map<String, Object>> jvmThreadsLiveMono = fetchMetric(webClient, baseUrl, "jvm.threads.live");
        Mono<Map<String, Object>> jvmThreadsPeakMono = fetchMetric(webClient, baseUrl, "jvm.threads.peak");
        Mono<Map<String, Object>> jvmUptimeMono = fetchMetric(webClient, baseUrl, "process.uptime");
        Mono<Map<String, Object>> jvmInfoMono = fetchMetric(webClient, baseUrl, "jvm.info");

        // Mono.zip only supports up to 8 arguments, so we'll zip in two groups
        Mono<reactor.util.function.Tuple8<Map<String, Object>, Map<String, Object>, Map<String, Object>, 
                Map<String, Object>, Map<String, Object>, Map<String, Object>, Map<String, Object>, Map<String, Object>>> firstGroup = 
                Mono.zip(allMetricsMono, jvmMemoryUsedMono, jvmMemoryMaxMono, jvmMemoryCommittedMono,
                        jvmNonHeapUsedMono, jvmThreadsLiveMono, jvmThreadsPeakMono, jvmUptimeMono);
        
        return Mono.zip(firstGroup, jvmInfoMono)
                .map(tuple -> {
                    log.info("=== STEP 2: All metrics fetched, parsing JVM info ===");
                    reactor.util.function.Tuple8<Map<String, Object>, Map<String, Object>, Map<String, Object>, 
                            Map<String, Object>, Map<String, Object>, Map<String, Object>, Map<String, Object>, Map<String, Object>> first = tuple.getT1();
                    Map<String, Object> jvmInfo = tuple.getT2();
                    
                    Map<String, Object> allMetrics = first.getT1();
                    Map<String, Object> memoryUsed = first.getT2();
                    Map<String, Object> memoryMax = first.getT3();
                    Map<String, Object> memoryCommitted = first.getT4();
                    Map<String, Object> nonHeapUsed = first.getT5();
                    Map<String, Object> threadsLive = first.getT6();
                    Map<String, Object> threadsPeak = first.getT7();
                    Map<String, Object> uptime = first.getT8();
                    
                    log.info("Metrics status - memoryUsed: {}, memoryMax: {}, threadsLive: {}, uptime: {}, jvmInfo: {}", 
                            memoryUsed != null && !memoryUsed.isEmpty(),
                            memoryMax != null && !memoryMax.isEmpty(),
                            threadsLive != null && !threadsLive.isEmpty(),
                            uptime != null && !uptime.isEmpty(),
                            jvmInfo != null && !jvmInfo.isEmpty());
                    
                    JvmInfo result = parseJvmInfo(service, allMetrics, memoryUsed, memoryMax, memoryCommitted, 
                            nonHeapUsed, threadsLive, threadsPeak, uptime, jvmInfo);
                    
                    log.info("=== STEP 3: JVM info parsed successfully ===");
                    log.info("Result - JVM: {}, Heap: {}/{}, Threads: {}, Uptime: {} ms", 
                            result.getJvmName(), result.getHeapUsed(), result.getHeapMax(), 
                            result.getThreadCount(), result.getUptime());
                    
                    return result;
                })
        .onErrorResume(e -> {
            log.error("=== ERROR: Failed to fetch JVM info from {} ===", service.getName(), e);
            log.error("Error type: {}, Message: {}", e.getClass().getSimpleName(), e.getMessage());
            if (e.getCause() != null) {
                log.error("Caused by: {}", e.getCause().getMessage());
            }
            return Mono.just(createEmptyJvmInfo(service));
        });
    }

    /**
     * Fetch a specific metric from actuator
     */
    @SuppressWarnings("unchecked")
    private Mono<Map<String, Object>> fetchMetric(WebClient webClient, String baseUrl, String metricName) {
        // Handle query parameters in metricName (e.g., "jvm.memory.used?tag=area:heap")
        String uri = baseUrl + "/metrics/" + metricName;
        log.debug("Fetching metric: {}", uri);
        return webClient.get()
                .uri(uri)
                .retrieve()
                .bodyToMono(Map.class)
                .cast(Map.class)
                .map(m -> (Map<String, Object>) m)
                .timeout(Duration.ofSeconds(10))
                .doOnNext(result -> {
                    if (result != null && !result.isEmpty()) {
                        log.debug("Successfully fetched metric {}: {} keys", metricName, result.size());
                    } else {
                        log.debug("Metric {} returned empty result", metricName);
                    }
                })
                .onErrorResume(e -> {
                    log.warn("Failed to fetch metric {} from {}: {}", metricName, baseUrl, e.getMessage());
                    return Mono.just(new HashMap<>());
                });
    }

    /**
     * Get local infrastructure information (for the management console itself).
     */
    public InfrastructureInfo getLocalInfrastructureInfo() {
        InfrastructureInfo info = new InfrastructureInfo();
        
        OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
        RuntimeMXBean runtimeBean = ManagementFactory.getRuntimeMXBean();
        
        // OS Information
        info.setOsName(System.getProperty("os.name"));
        info.setOsVersion(System.getProperty("os.version"));
        info.setOsArch(System.getProperty("os.arch"));
        info.setAvailableProcessors(osBean.getAvailableProcessors());
        
        // CPU Information
        if (osBean instanceof com.sun.management.OperatingSystemMXBean) {
            com.sun.management.OperatingSystemMXBean sunOsBean = 
                    (com.sun.management.OperatingSystemMXBean) osBean;
            info.setSystemCpuLoad(sunOsBean.getSystemCpuLoad() * 100);
            info.setProcessCpuLoad(sunOsBean.getProcessCpuLoad() * 100);
            info.setTotalPhysicalMemory(sunOsBean.getTotalPhysicalMemorySize());
            info.setFreePhysicalMemory(sunOsBean.getFreePhysicalMemorySize());
        }
        
        // JVM Information
        info.setJvmName(runtimeBean.getVmName());
        info.setJvmVersion(runtimeBean.getVmVersion());
        info.setJvmVendor(runtimeBean.getVmVendor());
        info.setJvmUptime(runtimeBean.getUptime());
        
        // System Properties
        Map<String, String> systemProps = new HashMap<>();
        System.getProperties().forEach((key, value) -> 
            systemProps.put(key.toString(), value.toString()));
        info.setSystemProperties(systemProps);
        
        return info;
    }

    // Helper methods

    private ManagedService getService(Long serviceId) {
        return serviceRepository.findById(serviceId)
                .orElseThrow(() -> new ResourceNotFoundException("Service not found: " + serviceId));
    }

    private String buildActuatorBaseUrl(ManagedService service) {
        String baseUrl = service.getActuatorUrl();
        if (baseUrl == null || baseUrl.isEmpty()) {
            baseUrl = String.format("http://%s:%d/actuator",
                    service.getHost() != null ? service.getHost() : "localhost",
                    service.getPort() != null ? service.getPort() : 8080);
        }
        // Ensure it doesn't end with /actuator/actuator
        if (baseUrl.endsWith("/actuator")) {
            log.debug("Built actuator base URL: {}", baseUrl);
            return baseUrl;
        }
        if (!baseUrl.contains("/actuator")) {
            baseUrl = baseUrl + "/actuator";
        }
        log.debug("Built actuator base URL: {}", baseUrl);
        return baseUrl;
    }

    @SuppressWarnings("unchecked")
    private InfrastructureInfo parseInfrastructureInfo(ManagedService service, 
            Map<String, Object> allMetrics, Map<String, Object> info,
            Map<String, Object> systemCpu, Map<String, Object> processCpu,
            Map<String, Object> systemLoad, Map<String, Object> processUptime) {
        InfrastructureInfo infrastructureInfo = new InfrastructureInfo();
        // Always set service info first
        infrastructureInfo.setServiceId(service.getId());
        infrastructureInfo.setServiceName(service.getName());
        infrastructureInfo.setInstanceId(service.getInstanceId());
        
        log.debug("Parsing infrastructure info for service: {}", service.getName());
        
        // Parse info endpoint response
        if (info != null) {
            Map<String, Object> build = (Map<String, Object>) info.get("build");
            if (build != null) {
                infrastructureInfo.setApplicationVersion((String) build.get("version"));
            }
            
            Map<String, Object> java = (Map<String, Object>) info.get("java");
            if (java != null) {
                Map<String, Object> version = (Map<String, Object>) java.get("version");
                if (version != null) {
                    infrastructureInfo.setJvmVersion((String) version.get("java.version"));
                    infrastructureInfo.setJvmVendor((String) version.get("java.vendor"));
                    // Try to get JVM name from runtime info
                    String runtimeName = (String) version.get("java.runtime.name");
                    if (runtimeName != null && !runtimeName.isEmpty()) {
                        infrastructureInfo.setJvmName(runtimeName);
                    } else {
                        // Fallback: try to construct from vendor and version
                        String vendor = (String) version.get("java.vendor");
                        String javaVersion = (String) version.get("java.version");
                        if (vendor != null && javaVersion != null) {
                            infrastructureInfo.setJvmName(vendor + " " + javaVersion);
                        }
                    }
                }
            }
        }
        
        // If JVM name is still null, try to extract from system properties or metrics
        if (infrastructureInfo.getJvmName() == null) {
            // Try to get from system properties (this is the local system, not remote)
            // For remote system, we need to get it from /actuator/metrics/jvm.info
            String jvmName = System.getProperty("java.vm.name");
            if (jvmName != null && !jvmName.isEmpty()) {
                infrastructureInfo.setJvmName(jvmName);
            }
        }
        
        // Extract system properties from metrics names
        if (allMetrics != null) {
            List<String> names = (List<String>) allMetrics.get("names");
            if (names != null) {
                // Extract OS info from system properties
                for (String name : names) {
                    if (name.startsWith("system.")) {
                        // System metrics available
                    }
                }
            }
        }
        
        // Parse CPU metrics
        Double systemCpuValue = extractMetricValue(systemCpu);
        if (systemCpuValue != null) {
            infrastructureInfo.setSystemCpuLoad(systemCpuValue * 100);
        }
        
        Double processCpuValue = extractMetricValue(processCpu);
        if (processCpuValue != null) {
            infrastructureInfo.setProcessCpuLoad(processCpuValue * 100);
        }
        
        // Extract OS information - try to get from remote service metrics first
        // If not available, use local system as fallback (this is a limitation - we can't get remote OS directly)
        // In a real implementation, you'd need the remote service to expose OS info via actuator
        try {
            // Try local system as fallback (this gives us the management console's OS, not the remote service's)
            // TODO: Remote services should expose OS info via actuator/info endpoint
            String osName = System.getProperty("os.name");
            String osVersion = System.getProperty("os.version");
            String osArch = System.getProperty("os.arch");
            
            if (osName != null) infrastructureInfo.setOsName(osName);
            if (osVersion != null) infrastructureInfo.setOsVersion(osVersion);
            if (osArch != null) infrastructureInfo.setOsArch(osArch);
            infrastructureInfo.setAvailableProcessors(Runtime.getRuntime().availableProcessors());
            
            log.debug("Set OS info: {} {} ({})", osName, osVersion, osArch);
        } catch (Exception e) {
            log.debug("Could not extract OS info: {}", e.getMessage());
            // Set defaults
            infrastructureInfo.setOsName("Unknown");
            infrastructureInfo.setOsVersion("Unknown");
            infrastructureInfo.setOsArch("Unknown");
        }
        
        // Try to get memory info from system properties (fallback)
        // Note: System memory metrics may not be available on all platforms
        try {
            com.sun.management.OperatingSystemMXBean osBean = 
                    (com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
            infrastructureInfo.setTotalPhysicalMemory(osBean.getTotalPhysicalMemorySize());
            infrastructureInfo.setFreePhysicalMemory(osBean.getFreePhysicalMemorySize());
        } catch (Exception e) {
            log.debug("Could not extract memory info: {}", e.getMessage());
        }
        
        // Ensure at least some values are set (not all null)
        if (infrastructureInfo.getJvmName() == null) {
            infrastructureInfo.setJvmName("Unknown JVM");
        }
        if (infrastructureInfo.getJvmVersion() == null) {
            infrastructureInfo.setJvmVersion("Unknown");
        }
        if (infrastructureInfo.getJvmVendor() == null) {
            infrastructureInfo.setJvmVendor("Unknown");
        }
        if (infrastructureInfo.getOsName() == null) {
            infrastructureInfo.setOsName("Unknown");
        }
        
        log.debug("Final infrastructure info - Service: {}, OS: {}, JVM: {}, CPU: {}%", 
                infrastructureInfo.getServiceName(),
                infrastructureInfo.getOsName(),
                infrastructureInfo.getJvmName(),
                infrastructureInfo.getSystemCpuLoad());
        
        return infrastructureInfo;
    }

    @SuppressWarnings("unchecked")
    private JvmInfo parseJvmInfo(ManagedService service, 
            Map<String, Object> allMetrics,
            Map<String, Object> memoryUsed, Map<String, Object> memoryMax,
            Map<String, Object> memoryCommitted, Map<String, Object> nonHeapUsed,
            Map<String, Object> threadsLive,
            Map<String, Object> threadsPeak, Map<String, Object> uptime,
            Map<String, Object> jvmInfo) {
        JvmInfo info = new JvmInfo();
        info.setServiceId(service.getId());
        info.setServiceName(service.getName());
        info.setInstanceId(service.getInstanceId());
        
        log.info("Parsing JVM info for service: {}", service.getName());
        log.info("Metrics available - memoryUsed: {}, memoryMax: {}, threadsLive: {}, uptime: {}, jvmInfo: {}", 
                memoryUsed != null && !memoryUsed.isEmpty(),
                memoryMax != null && !memoryMax.isEmpty(),
                threadsLive != null && !threadsLive.isEmpty(),
                uptime != null && !uptime.isEmpty(),
                jvmInfo != null && !jvmInfo.isEmpty());
        
        // Extract JVM name, version, vendor from jvm.info metric
        if (jvmInfo != null && !jvmInfo.isEmpty()) {
            List<Map<String, Object>> measurements = (List<Map<String, Object>>) jvmInfo.get("measurements");
            if (measurements != null && !measurements.isEmpty()) {
                Map<String, Object> firstMeasurement = measurements.get(0);
                Object value = firstMeasurement.get("value");
                if (value instanceof String) {
                    String jvmInfoStr = (String) value;
                    log.debug("JVM info string: {}", jvmInfoStr);
                    // Parse JVM info string (format: "Java HotSpot(TM) 64-Bit Server VM 17.0.1+12-LTS (Oracle Corporation)")
                    if (jvmInfoStr.contains("VM")) {
                        String[] parts = jvmInfoStr.split("VM");
                        if (parts.length > 0) {
                            info.setJvmName(parts[0].trim() + " VM");
                        }
                        if (parts.length > 1) {
                            String versionPart = parts[1].trim();
                            // Extract version (before parenthesis)
                            String[] versionParts = versionPart.split("\\(");
                            if (versionParts.length > 0) {
                                info.setJvmVersion(versionParts[0].trim());
                            }
                            // Extract vendor (in parenthesis)
                            if (versionPart.contains("(") && versionPart.contains(")")) {
                                String vendor = versionPart.substring(versionPart.indexOf("(") + 1, versionPart.indexOf(")"));
                                info.setJvmVendor(vendor);
                            }
                        }
                    }
                }
            }
        }
        
        // Extract heap memory
        Long heapUsedValue = extractMetricValueAsLong(memoryUsed);
        if (heapUsedValue != null) {
            info.setHeapUsed(heapUsedValue);
            log.debug("Heap used: {} bytes", heapUsedValue);
        }
        
        Long heapMaxValue = extractMetricValueAsLong(memoryMax);
        if (heapMaxValue != null) {
            info.setHeapMax(heapMaxValue);
            log.debug("Heap max: {} bytes", heapMaxValue);
        }
        
        // Extract non-heap memory
        Long nonHeapUsedValue = extractMetricValueAsLong(nonHeapUsed);
        if (nonHeapUsedValue != null) {
            info.setNonHeapUsed(nonHeapUsedValue);
            log.debug("Non-heap used: {} bytes", nonHeapUsedValue);
        }
        
        // Extract thread count
        Double threadsLiveValue = extractMetricValue(threadsLive);
        if (threadsLiveValue != null) {
            info.setThreadCount(threadsLiveValue.intValue());
            log.debug("Threads live: {}", info.getThreadCount());
        }
        
        // Extract uptime (process.uptime is in seconds)
        Double uptimeValue = extractMetricValue(uptime);
        if (uptimeValue != null) {
            info.setUptime(uptimeValue.longValue() * 1000); // Convert seconds to milliseconds
            log.debug("Uptime: {} ms", info.getUptime());
        }
        
        // If JVM info not available from metrics, try to get from system properties
        if (info.getJvmName() == null || info.getJvmVersion() == null || info.getJvmVendor() == null) {
            try {
                RuntimeMXBean runtimeBean = ManagementFactory.getRuntimeMXBean();
                if (info.getJvmName() == null) {
                    info.setJvmName(runtimeBean.getVmName());
                }
                if (info.getJvmVersion() == null) {
                    info.setJvmVersion(runtimeBean.getVmVersion());
                }
                if (info.getJvmVendor() == null) {
                    info.setJvmVendor(runtimeBean.getVmVendor());
                }
                log.debug("Set JVM info from system: {} {} ({})", info.getJvmName(), info.getJvmVersion(), info.getJvmVendor());
            } catch (Exception e) {
                log.debug("Could not extract JVM info from system: {}", e.getMessage());
            }
        }
        
        log.debug("Final JVM info - Service: {}, JVM: {}, Heap: {}/{}, Threads: {}", 
                info.getServiceName(), info.getJvmName(), info.getHeapUsed(), info.getHeapMax(), info.getThreadCount());
        
        return info;
    }
    
    /**
     * Extract metric value from actuator metrics response
     */
    @SuppressWarnings("unchecked")
    private Double extractMetricValue(Map<String, Object> metricResponse) {
        if (metricResponse == null || metricResponse.isEmpty()) {
            return null;
        }
        
        List<Map<String, Object>> measurements = (List<Map<String, Object>>) metricResponse.get("measurements");
        if (measurements != null && !measurements.isEmpty()) {
            Map<String, Object> firstMeasurement = measurements.get(0);
            Object value = firstMeasurement.get("value");
            if (value instanceof Number) {
                return ((Number) value).doubleValue();
            }
        }
        
        return null;
    }
    
    /**
     * Extract metric value as Long from actuator metrics response
     */
    @SuppressWarnings("unchecked")
    private Long extractMetricValueAsLong(Map<String, Object> metricResponse) {
        if (metricResponse == null || metricResponse.isEmpty()) {
            return null;
        }
        
        List<Map<String, Object>> measurements = (List<Map<String, Object>>) metricResponse.get("measurements");
        if (measurements != null && !measurements.isEmpty()) {
            Map<String, Object> firstMeasurement = measurements.get(0);
            Object value = firstMeasurement.get("value");
            if (value instanceof Number) {
                return ((Number) value).longValue();
            }
        }
        
        return null;
    }

    private InfrastructureInfo createEmptyInfrastructureInfo(ManagedService service) {
        InfrastructureInfo info = new InfrastructureInfo();
        info.setServiceId(service.getId());
        info.setServiceName(service.getName());
        info.setInstanceId(service.getInstanceId());
        // Set default values to ensure JSON is not empty
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

    private JvmInfo createEmptyJvmInfo(ManagedService service) {
        JvmInfo info = new JvmInfo();
        info.setServiceId(service.getId());
        info.setServiceName(service.getName());
        info.setInstanceId(service.getInstanceId());
        // Set default values to ensure JSON is not completely empty
        info.setJvmName("Unknown");
        info.setJvmVersion("Unknown");
        info.setJvmVendor("Unknown");
        info.setHeapUsed(0L);
        info.setHeapMax(0L);
        info.setNonHeapUsed(0L);
        info.setThreadCount(0);
        info.setUptime(0L);
        log.debug("Created empty JVM info for service: {}", service.getName());
        return info;
    }

    // DTOs

    @Data
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.ALWAYS)
    public static class InfrastructureInfo {
        private Long serviceId;
        private String serviceName;
        private String instanceId;
        private String osName;
        private String osVersion;
        private String osArch;
        private Integer availableProcessors;
        private Double systemCpuLoad;
        private Double processCpuLoad;
        private Long totalPhysicalMemory;
        private Long freePhysicalMemory;
        private String jvmName;
        private String jvmVersion;
        private String jvmVendor;
        private Long jvmUptime;
        private String applicationVersion;
        private Map<String, String> systemProperties;
    }

    @Data
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.ALWAYS)
    public static class JvmInfo {
        private Long serviceId;
        private String serviceName;
        private String instanceId;
        private String jvmName;
        private String jvmVersion;
        private String jvmVendor;
        private Long heapUsed;
        private Long heapMax;
        private Long nonHeapUsed;
        private Integer threadCount;
        private Long uptime;
    }
}



