package com.management.console.domain.entity;

import com.management.console.domain.enums.ActionStatus;
import com.management.console.domain.enums.ServiceAction;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "audit_logs", indexes = {
    @Index(name = "idx_audit_timestamp", columnList = "timestamp"),
    @Index(name = "idx_audit_service", columnList = "service_id"),
    @Index(name = "idx_audit_user", columnList = "username")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_id")
    private ManagedService service;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    // WHO
    @Column(nullable = false)
    private String username;

    private String userRole;

    private String ipAddress;

    private String userAgent;

    // WHAT
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ServiceAction action;

    @Column(length = 1000)
    private String actionDetails;

    // Request details
    @Column(columnDefinition = "TEXT")
    private String requestPayload;

    // WHEN
    private LocalDateTime actionStartTime;

    private LocalDateTime actionEndTime;

    private Long durationMs;

    // WHY
    @Column(length = 500)
    private String reason;

    private Boolean isAutomated;

    private String automationSource;

    // OUTCOME
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ActionStatus status;

    @Column(length = 2000)
    private String resultMessage;

    @Column(columnDefinition = "TEXT")
    private String errorDetails;

    // Before/After state
    @Column(columnDefinition = "TEXT")
    private String previousState;

    @Column(columnDefinition = "TEXT")
    private String newState;

    // AI involvement
    private Boolean aiRecommended;

    @Column(length = 500)
    private String aiRecommendation;

    private Double aiConfidence;

    // Risk assessment
    private Integer riskLevel;

    private Boolean confirmationRequired;

    private Boolean confirmed;

    @PrePersist
    protected void onCreate() {
        if (timestamp == null) {
            timestamp = LocalDateTime.now();
        }
    }
}

