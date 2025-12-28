package com.management.console.dto;

import com.management.console.domain.enums.IncidentSeverity;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IncidentDTO {
    private Long id;
    private Long serviceId;
    private String serviceName;
    private String title;
    private String description;
    private IncidentSeverity severity;
    private String status;
    private String detectionSource;
    private String aiSummary;
    private String aiRecommendation;
    private Double aiConfidence;
    private LocalDateTime createdAt;
    private LocalDateTime acknowledgedAt;
    private String acknowledgedBy;
    private LocalDateTime resolvedAt;
    private String resolvedBy;
    private String resolution;
    private Integer affectedUsers;
    private Double errorRateIncrease;
    private Double latencyIncrease;
    private List<String> tags;
}

