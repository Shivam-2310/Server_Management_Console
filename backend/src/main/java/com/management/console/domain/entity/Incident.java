package com.management.console.domain.entity;

import com.management.console.domain.enums.IncidentSeverity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "incidents", indexes = {
    @Index(name = "idx_incident_service", columnList = "service_id"),
    @Index(name = "idx_incident_status", columnList = "status"),
    @Index(name = "idx_incident_created", columnList = "created_at")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Incident {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_id", nullable = false)
    private ManagedService service;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IncidentSeverity severity;

    @Column(nullable = false)
    private String status; // OPEN, INVESTIGATING, RESOLVED, CLOSED

    // Detection info
    private String detectionSource; // HEALTH_CHECK, METRICS, AI_ANOMALY, MANUAL

    private String detectionRule;

    // AI-generated summary
    @Column(columnDefinition = "TEXT")
    private String aiSummary;

    @Column(columnDefinition = "TEXT")
    private String aiRecommendation;

    private Double aiConfidence;

    // Timeline
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime acknowledgedAt;

    private String acknowledgedBy;

    private LocalDateTime resolvedAt;

    private String resolvedBy;

    @Column(length = 1000)
    private String resolution;

    private LocalDateTime closedAt;

    // Impact assessment
    private Integer affectedUsers;

    private Double errorRateIncrease;

    private Double latencyIncrease;

    // Related metrics at time of incident
    private Double cpuAtIncident;

    private Double memoryAtIncident;

    private Double errorRateAtIncident;

    private Long responseTimeAtIncident;

    // Related incidents
    @ElementCollection
    @CollectionTable(name = "incident_related", joinColumns = @JoinColumn(name = "incident_id"))
    @Column(name = "related_incident_id")
    @Builder.Default
    private List<Long> relatedIncidentIds = new ArrayList<>();

    // Tags
    @ElementCollection
    @CollectionTable(name = "incident_tags", joinColumns = @JoinColumn(name = "incident_id"))
    @Column(name = "tag")
    @Builder.Default
    private List<String> tags = new ArrayList<>();

    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) {
            status = "OPEN";
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}

