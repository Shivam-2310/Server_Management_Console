package com.management.console.dto;

import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AIAnalysisDTO {
    private Long serviceId;
    private String serviceName;
    private LocalDateTime analysisTime;
    
    // Health assessment
    private String healthAssessment;
    private Double confidence;
    
    // Risk analysis
    private Integer riskScore;
    private String riskTrend;
    private List<String> riskFactors;
    
    // Anomaly detection
    private Boolean anomalyDetected;
    private String anomalyType;
    private String anomalyDescription;
    
    // Recommendations
    private List<AIRecommendation> recommendations;
    
    // Incident summary (if applicable)
    private String incidentSummary;
    
    // Trend analysis
    private String trendAnalysis;
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AIRecommendation {
        private String action;
        private String reason;
        private String urgency; // LOW, MEDIUM, HIGH, CRITICAL
        private Double confidence;
        private Boolean requiresConfirmation;
    }
}

