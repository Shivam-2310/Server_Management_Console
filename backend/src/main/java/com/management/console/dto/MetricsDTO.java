package com.management.console.dto;

import lombok.*;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MetricsDTO {
    private Long id;
    private Long serviceId;
    private String serviceName;
    private LocalDateTime timestamp;
    
    // CPU
    private Double cpuUsage;
    private Double systemCpuUsage;
    private Integer cpuCount;
    
    // Memory
    private Long memoryUsed;
    private Long memoryMax;
    private Double memoryUsagePercent;
    
    // Heap (JVM)
    private Long heapUsed;
    private Long heapMax;
    
    // Threads
    private Integer threadCount;
    private Integer threadPeakCount;
    
    // GC
    private Long gcPauseCount;
    private Double gcPauseTime;
    
    // HTTP
    private Long httpRequestsTotal;
    private Double httpRequestsPerSecond;
    private Double averageResponseTime;
    private Double p95ResponseTime;
    private Double p99ResponseTime;
    
    // Errors
    private Long errorCount;
    private Double errorRate;
    
    // Uptime
    private Long uptimeSeconds;
    
    // Frontend specific
    private Double pageLoadTime;
    private Double firstContentfulPaint;
    private Double largestContentfulPaint;
}

