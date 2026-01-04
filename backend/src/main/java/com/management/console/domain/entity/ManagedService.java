package com.management.console.domain.entity;

import com.management.console.domain.enums.HealthStatus;
import com.management.console.domain.enums.ServiceType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "managed_services")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ManagedService {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ServiceType serviceType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private HealthStatus healthStatus = HealthStatus.UNKNOWN;

    // Connection details
    @Column(nullable = false)
    private String host;

    @Column(nullable = false)
    private Integer port;

    private String healthEndpoint;

    private String metricsEndpoint;

    private String baseUrl;

    // For Spring Boot services
    private String actuatorBasePath;

    // For Frontend services
    private String frontendTechnology; // React, Angular, Vue, etc.
    private String servingTechnology;  // Nginx, Node, Static, etc.

    // Lifecycle management
    private String startCommand;
    private String stopCommand;
    private String restartCommand;

    // Working directory for commands
    private String workingDirectory;

    // Process management
    private String processIdentifier; // PID file path or process name

    // Current state
    @Builder.Default
    private Boolean isRunning = false;

    @Builder.Default
    private Integer instanceCount = 1;

    // Metrics snapshot
    private Double cpuUsage;
    private Double memoryUsage;
    private Long responseTime;
    private Double errorRate;

    // Risk scoring (AI-derived)
    @Builder.Default
    private Integer stabilityScore = 100;

    @Builder.Default
    private Integer riskScore = 0;

    private String riskTrend; // IMPROVING, STABLE, DEGRADING

    // Timestamps
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private LocalDateTime lastHealthCheck;

    private LocalDateTime lastMetricsCollection;

    private LocalDateTime lastRestart;

    // Tags for organization
    @ElementCollection
    @CollectionTable(name = "service_tags", joinColumns = @JoinColumn(name = "service_id"))
    @Column(name = "tag")
    @Builder.Default
    private List<String> tags = new ArrayList<>();

    // Environment
    private String environment; // DEV, STAGING, PROD

    @Builder.Default
    private Boolean enabled = true;

    // Authentication token for service-to-console communication
    @Column(unique = true, length = 128)
    private String authenticationToken;

    // Service instance identifier (for horizontally scaled services)
    private String instanceId;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (updatedAt == null) {
            updatedAt = LocalDateTime.now();
        }
        // Ensure tags is never null
        if (tags == null) {
            tags = new ArrayList<>();
        }
        // Ensure default values are set
        if (healthStatus == null) {
            healthStatus = HealthStatus.UNKNOWN;
        }
        if (isRunning == null) {
            isRunning = false;
        }
        if (instanceCount == null) {
            instanceCount = 1;
        }
        if (stabilityScore == null) {
            stabilityScore = 100;
        }
        if (riskScore == null) {
            riskScore = 0;
        }
        if (enabled == null) {
            enabled = true;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
        // Ensure tags is never null
        if (tags == null) {
            tags = new ArrayList<>();
        }
    }

    public String getFullUrl() {
        if (baseUrl != null && !baseUrl.isEmpty()) {
            return baseUrl;
        }
        return String.format("http://%s:%d", host, port);
    }

    public String getActuatorUrl() {
        String base = getFullUrl();
        String actuatorPath = actuatorBasePath != null ? actuatorBasePath : "/actuator";
        return base + actuatorPath;
    }
}

