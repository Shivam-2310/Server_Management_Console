package com.management.console.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "service_metrics", indexes = {
    @Index(name = "idx_metrics_service_timestamp", columnList = "service_id, timestamp")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ServiceMetrics {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_id", nullable = false)
    private ManagedService service;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    // CPU metrics
    private Double cpuUsage;
    private Double systemCpuUsage;
    private Integer cpuCount;

    // Memory metrics
    private Long memoryUsed;
    private Long memoryMax;
    private Long memoryCommitted;
    private Double memoryUsagePercent;

    // Heap memory (JVM)
    private Long heapUsed;
    private Long heapMax;
    private Long heapCommitted;

    // Non-heap memory (JVM)
    private Long nonHeapUsed;
    private Long nonHeapCommitted;

    // Thread metrics
    private Integer threadCount;
    private Integer threadPeakCount;
    private Integer threadDaemonCount;

    // GC metrics
    private Long gcPauseCount;
    private Double gcPauseTime;

    // HTTP metrics
    private Long httpRequestsTotal;
    private Double httpRequestsPerSecond;
    private Double averageResponseTime;
    private Double p95ResponseTime;
    private Double p99ResponseTime;

    // Error metrics
    private Long errorCount;
    private Double errorRate;
    private Long http4xxCount;
    private Long http5xxCount;

    // Uptime
    private Long uptimeSeconds;

    // Disk metrics
    private Long diskUsed;
    private Long diskFree;
    private Long diskTotal;

    // Network metrics
    private Long bytesReceived;
    private Long bytesSent;
    private Long connectionsActive;

    // Frontend-specific metrics
    private Double pageLoadTime;
    private Double firstContentfulPaint;
    private Double largestContentfulPaint;
    private Long bundleSize;
    private Boolean assetsAvailable;

    // Custom metrics (JSON stored)
    @Column(columnDefinition = "TEXT")
    private String customMetrics;

    @PrePersist
    protected void onCreate() {
        if (timestamp == null) {
            timestamp = LocalDateTime.now();
        }
    }
}

