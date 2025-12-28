package com.management.console.dto;

import com.management.console.domain.enums.HealthStatus;
import com.management.console.domain.enums.ServiceType;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ServiceDTO {
    private Long id;
    private String name;
    private String description;
    private ServiceType serviceType;
    private HealthStatus healthStatus;
    private String host;
    private Integer port;
    private String healthEndpoint;
    private String metricsEndpoint;
    private String baseUrl;
    private String actuatorBasePath;
    private String frontendTechnology;
    private String servingTechnology;
    private String startCommand;
    private String stopCommand;
    private String restartCommand;
    private String workingDirectory;
    private String processIdentifier;
    private Boolean isRunning;
    private Integer instanceCount;
    private Double cpuUsage;
    private Double memoryUsage;
    private Long responseTime;
    private Double errorRate;
    private Integer stabilityScore;
    private Integer riskScore;
    private String riskTrend;
    private LocalDateTime lastHealthCheck;
    private LocalDateTime lastMetricsCollection;
    private LocalDateTime lastRestart;
    private List<String> tags;
    private String environment;
    private Boolean enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

