package com.management.console.dto;

import com.management.console.domain.enums.HealthStatus;
import lombok.*;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HealthCheckDTO {
    private Long id;
    private Long serviceId;
    private String serviceName;
    private LocalDateTime timestamp;
    private HealthStatus status;
    private Long responseTimeMs;
    private Integer httpStatusCode;
    private String statusMessage;
    private Map<String, Object> componentDetails;
    private Long diskSpaceFree;
    private Long diskSpaceTotal;
    private Boolean databaseHealthy;
    private String checkType;
    private String errorMessage;
}

