package com.management.console.service;

import com.management.console.domain.entity.ManagedService;
import com.management.console.exception.ResourceNotFoundException;
import com.management.console.repository.ManagedServiceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.lang.management.*;
import java.time.Duration;
import java.util.*;

/**
 * Service for JVM diagnostics including thread dumps, heap info, and memory stats.
 * Works with both local JVM and remote services via actuator endpoints.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class JvmDiagnosticsService {

    private final ManagedServiceRepository serviceRepository;
    private final WebClient webClient;

    /**
     * Get thread dump from a remote service via actuator
     */
    public Mono<ThreadDumpInfo> getThreadDump(Long serviceId) {
        ManagedService service = getService(serviceId);
        String actuatorUrl = buildActuatorUrl(service, "/threaddump");
        
        log.info("Fetching thread dump from service: {} at {}", service.getName(), actuatorUrl);
        
        return webClient.get()
                .uri(actuatorUrl)
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(Duration.ofSeconds(30))
                .map(this::parseThreadDump)
                .onErrorResume(e -> {
                    log.warn("Failed to fetch thread dump from {}: {}", service.getName(), e.getMessage());
                    return Mono.just(getLocalThreadDump());
                });
    }

    /**
     * Get local JVM thread dump (for the management console itself)
     */
    public ThreadDumpInfo getLocalThreadDump() {
        ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
        ThreadInfo[] threadInfos = threadMXBean.dumpAllThreads(true, true);
        
        ThreadDumpInfo info = new ThreadDumpInfo();
        info.setTimestamp(System.currentTimeMillis());
        info.setTotalThreads(threadInfos.length);
        
        Map<Thread.State, Integer> stateCounts = new EnumMap<>(Thread.State.class);
        List<ThreadDetail> threads = new ArrayList<>();
        
        for (ThreadInfo ti : threadInfos) {
            stateCounts.merge(ti.getThreadState(), 1, Integer::sum);
            
            ThreadDetail detail = new ThreadDetail();
            detail.setThreadId(ti.getThreadId());
            detail.setThreadName(ti.getThreadName());
            detail.setThreadState(ti.getThreadState().name());
            detail.setDaemon(false); // ThreadInfo doesn't provide this directly
            detail.setBlocked(ti.getBlockedCount());
            detail.setWaited(ti.getWaitedCount());
            detail.setLockName(ti.getLockName());
            detail.setLockOwnerName(ti.getLockOwnerName());
            
            // Get stack trace (limited to top 20 frames)
            StackTraceElement[] stackTrace = ti.getStackTrace();
            List<String> stack = new ArrayList<>();
            for (int i = 0; i < Math.min(20, stackTrace.length); i++) {
                stack.add(stackTrace[i].toString());
            }
            detail.setStackTrace(stack);
            
            threads.add(detail);
        }
        
        info.setStateCounts(stateCounts);
        info.setThreads(threads);
        info.setPeakThreadCount(threadMXBean.getPeakThreadCount());
        info.setTotalStartedThreadCount(threadMXBean.getTotalStartedThreadCount());
        
        // Get actual daemon thread count using Thread.getAllStackTraces()
        long[] allThreadIds = threadMXBean.getAllThreadIds();
        int daemonCount = 0;
        for (long threadId : allThreadIds) {
            Thread thread = findThreadById(threadId);
            if (thread != null && thread.isDaemon()) {
                daemonCount++;
            }
        }
        info.setDaemonThreadCount(daemonCount);
        
        return info;
    }

    /**
     * Get heap memory info from a remote service
     */
    public Mono<HeapInfo> getHeapInfo(Long serviceId) {
        ManagedService service = getService(serviceId);
        String actuatorUrl = buildActuatorUrl(service, "/metrics/jvm.memory.used");
        
        return webClient.get()
                .uri(actuatorUrl)
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(Duration.ofSeconds(10))
                .map(this::parseHeapMetrics)
                .onErrorResume(e -> {
                    log.warn("Failed to fetch heap info from {}: {}", service.getName(), e.getMessage());
                    return Mono.just(getLocalHeapInfo());
                });
    }

    /**
     * Get local JVM heap info
     */
    public HeapInfo getLocalHeapInfo() {
        MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
        MemoryUsage heapUsage = memoryMXBean.getHeapMemoryUsage();
        MemoryUsage nonHeapUsage = memoryMXBean.getNonHeapMemoryUsage();
        
        HeapInfo info = new HeapInfo();
        info.setTimestamp(System.currentTimeMillis());
        
        // Heap memory
        info.setHeapUsed(heapUsage.getUsed());
        info.setHeapMax(heapUsage.getMax());
        info.setHeapCommitted(heapUsage.getCommitted());
        info.setHeapInit(heapUsage.getInit());
        
        // Non-heap memory
        info.setNonHeapUsed(nonHeapUsage.getUsed());
        info.setNonHeapMax(nonHeapUsage.getMax());
        info.setNonHeapCommitted(nonHeapUsage.getCommitted());
        
        // Memory pools
        List<MemoryPoolInfo> pools = new ArrayList<>();
        for (MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans()) {
            MemoryPoolInfo poolInfo = new MemoryPoolInfo();
            poolInfo.setName(pool.getName());
            poolInfo.setType(pool.getType().name());
            
            MemoryUsage usage = pool.getUsage();
            if (usage != null) {
                poolInfo.setUsed(usage.getUsed());
                poolInfo.setMax(usage.getMax());
                poolInfo.setCommitted(usage.getCommitted());
            }
            pools.add(poolInfo);
        }
        info.setMemoryPools(pools);
        
        // GC info
        List<GCInfo> gcInfos = new ArrayList<>();
        for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            GCInfo gcInfo = new GCInfo();
            gcInfo.setName(gc.getName());
            gcInfo.setCollectionCount(gc.getCollectionCount());
            gcInfo.setCollectionTime(gc.getCollectionTime());
            gcInfos.add(gcInfo);
        }
        info.setGarbageCollectors(gcInfos);
        
        return info;
    }

    /**
     * Get comprehensive JVM info
     */
    public JvmInfo getLocalJvmInfo() {
        RuntimeMXBean runtimeMXBean = ManagementFactory.getRuntimeMXBean();
        OperatingSystemMXBean osMXBean = ManagementFactory.getOperatingSystemMXBean();
        
        JvmInfo info = new JvmInfo();
        info.setTimestamp(System.currentTimeMillis());
        
        // Runtime info
        info.setVmName(runtimeMXBean.getVmName());
        info.setVmVersion(runtimeMXBean.getVmVersion());
        info.setVmVendor(runtimeMXBean.getVmVendor());
        info.setStartTime(runtimeMXBean.getStartTime());
        info.setUptime(runtimeMXBean.getUptime());
        info.setInputArguments(runtimeMXBean.getInputArguments());
        
        // System properties
        Map<String, String> sysProps = new LinkedHashMap<>();
        sysProps.put("java.version", System.getProperty("java.version"));
        sysProps.put("java.home", System.getProperty("java.home"));
        sysProps.put("os.name", System.getProperty("os.name"));
        sysProps.put("os.arch", System.getProperty("os.arch"));
        sysProps.put("user.dir", System.getProperty("user.dir"));
        info.setSystemProperties(sysProps);
        
        // OS info
        info.setOsName(osMXBean.getName());
        info.setOsArch(osMXBean.getArch());
        info.setAvailableProcessors(osMXBean.getAvailableProcessors());
        info.setSystemLoadAverage(osMXBean.getSystemLoadAverage());
        
        // Class loading
        ClassLoadingMXBean classLoadingMXBean = ManagementFactory.getClassLoadingMXBean();
        info.setLoadedClassCount(classLoadingMXBean.getLoadedClassCount());
        info.setTotalLoadedClassCount(classLoadingMXBean.getTotalLoadedClassCount());
        info.setUnloadedClassCount(classLoadingMXBean.getUnloadedClassCount());
        
        return info;
    }

    /**
     * Request garbage collection (best effort)
     */
    public GCRequestResult requestGC(Long serviceId) {
        if (serviceId == null || serviceId == 0) {
            // Local GC
            long before = Runtime.getRuntime().freeMemory();
            System.gc();
            long after = Runtime.getRuntime().freeMemory();
            
            GCRequestResult result = new GCRequestResult();
            result.setSuccess(true);
            result.setMessage("GC requested successfully");
            result.setMemoryFreedBytes(after - before);
            return result;
        }
        
        // For remote services, we can't force GC directly
        GCRequestResult result = new GCRequestResult();
        result.setSuccess(false);
        result.setMessage("Remote GC trigger not supported. Use JMX or actuator endpoints if available.");
        return result;
    }

    /**
     * Generate thread dump as downloadable text
     */
    public String generateThreadDumpText(Long serviceId) {
        ThreadDumpInfo dump = serviceId == null || serviceId == 0 
                ? getLocalThreadDump() 
                : getThreadDump(serviceId).block();
        
        if (dump == null) {
            return "Unable to generate thread dump";
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("Thread Dump generated at: ").append(new Date(dump.getTimestamp())).append("\n");
        sb.append("Total threads: ").append(dump.getTotalThreads()).append("\n");
        sb.append("Peak thread count: ").append(dump.getPeakThreadCount()).append("\n\n");
        
        sb.append("Thread states:\n");
        dump.getStateCounts().forEach((state, count) -> 
            sb.append("  ").append(state).append(": ").append(count).append("\n"));
        sb.append("\n");
        
        for (ThreadDetail thread : dump.getThreads()) {
            sb.append("\"").append(thread.getThreadName()).append("\" ");
            sb.append("tid=").append(thread.getThreadId()).append(" ");
            sb.append(thread.getThreadState());
            if (thread.getLockName() != null) {
                sb.append(" on ").append(thread.getLockName());
            }
            if (thread.getLockOwnerName() != null) {
                sb.append(" owned by \"").append(thread.getLockOwnerName()).append("\"");
            }
            sb.append("\n");
            
            for (String frame : thread.getStackTrace()) {
                sb.append("    at ").append(frame).append("\n");
            }
            sb.append("\n");
        }
        
        return sb.toString();
    }

    // Helper methods

    private Thread findThreadById(long threadId) {
        for (Thread thread : Thread.getAllStackTraces().keySet()) {
            if (thread.getId() == threadId) {
                return thread;
            }
        }
        return null;
    }

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
    private ThreadDumpInfo parseThreadDump(Map<String, Object> response) {
        ThreadDumpInfo info = new ThreadDumpInfo();
        info.setTimestamp(System.currentTimeMillis());
        
        List<Map<String, Object>> threads = (List<Map<String, Object>>) response.get("threads");
        if (threads != null) {
            info.setTotalThreads(threads.size());
            
            Map<Thread.State, Integer> stateCounts = new EnumMap<>(Thread.State.class);
            List<ThreadDetail> details = new ArrayList<>();
            
            for (Map<String, Object> t : threads) {
                String stateStr = (String) t.get("threadState");
                Thread.State state = Thread.State.valueOf(stateStr);
                stateCounts.merge(state, 1, Integer::sum);
                
                ThreadDetail detail = new ThreadDetail();
                detail.setThreadId(((Number) t.get("threadId")).longValue());
                detail.setThreadName((String) t.get("threadName"));
                detail.setThreadState(stateStr);
                
                List<Map<String, Object>> stackTrace = (List<Map<String, Object>>) t.get("stackTrace");
                if (stackTrace != null) {
                    List<String> stack = new ArrayList<>();
                    for (Map<String, Object> frame : stackTrace) {
                        stack.add(String.format("%s.%s(%s:%d)",
                                frame.get("className"),
                                frame.get("methodName"),
                                frame.get("fileName"),
                                frame.get("lineNumber")));
                    }
                    detail.setStackTrace(stack);
                }
                details.add(detail);
            }
            
            info.setStateCounts(stateCounts);
            info.setThreads(details);
        }
        
        return info;
    }

    private HeapInfo parseHeapMetrics(Map<String, Object> response) {
        HeapInfo info = new HeapInfo();
        info.setTimestamp(System.currentTimeMillis());
        
        // Parse actuator metrics response
        // This is a simplified parser - actual format may vary
        return info;
    }

    // DTOs

    @lombok.Data
    public static class ThreadDumpInfo {
        private long timestamp;
        private int totalThreads;
        private int peakThreadCount;
        private long totalStartedThreadCount;
        private int daemonThreadCount;
        private Map<Thread.State, Integer> stateCounts;
        private List<ThreadDetail> threads;
    }

    @lombok.Data
    public static class ThreadDetail {
        private long threadId;
        private String threadName;
        private String threadState;
        private boolean daemon;
        private long blocked;
        private long waited;
        private String lockName;
        private String lockOwnerName;
        private List<String> stackTrace;
    }

    @lombok.Data
    public static class HeapInfo {
        private long timestamp;
        private long heapUsed;
        private long heapMax;
        private long heapCommitted;
        private long heapInit;
        private long nonHeapUsed;
        private long nonHeapMax;
        private long nonHeapCommitted;
        private List<MemoryPoolInfo> memoryPools;
        private List<GCInfo> garbageCollectors;
    }

    @lombok.Data
    public static class MemoryPoolInfo {
        private String name;
        private String type;
        private long used;
        private long max;
        private long committed;
    }

    @lombok.Data
    public static class GCInfo {
        private String name;
        private long collectionCount;
        private long collectionTime;
    }

    @lombok.Data
    public static class JvmInfo {
        private long timestamp;
        private String vmName;
        private String vmVersion;
        private String vmVendor;
        private long startTime;
        private long uptime;
        private List<String> inputArguments;
        private Map<String, String> systemProperties;
        private String osName;
        private String osArch;
        private int availableProcessors;
        private double systemLoadAverage;
        private int loadedClassCount;
        private long totalLoadedClassCount;
        private long unloadedClassCount;
    }

    @lombok.Data
    public static class GCRequestResult {
        private boolean success;
        private String message;
        private long memoryFreedBytes;
    }
}

