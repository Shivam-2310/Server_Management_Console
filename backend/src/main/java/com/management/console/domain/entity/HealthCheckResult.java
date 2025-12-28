package com.management.console.domain.entity;

import com.management.console.domain.enums.HealthStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "health_check_results", indexes = {
    @Index(name = "idx_health_service_timestamp", columnList = "service_id, timestamp")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HealthCheckResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_id", nullable = false)
    private ManagedService service;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private HealthStatus status;

    private Long responseTimeMs;

    private Integer httpStatusCode;

    @Column(length = 2000)
    private String statusMessage;

    // Component health details (JSON)
    @Column(columnDefinition = "TEXT")
    private String componentDetails;

    // Disk space info
    private Long diskSpaceFree;
    private Long diskSpaceTotal;

    // Database health (if applicable)
    private Boolean databaseHealthy;
    private String databaseStatus;

    // Custom health indicators
    @Column(columnDefinition = "TEXT")
    private String customIndicators;

    // Error details if check failed
    @Column(length = 2000)
    private String errorMessage;

    private String errorType;

    // Check type
    private String checkType; // ACTUATOR, HTTP, SYNTHETIC, PING

    @PrePersist
    protected void onCreate() {
        if (timestamp == null) {
            timestamp = LocalDateTime.now();
        }
    }
}

