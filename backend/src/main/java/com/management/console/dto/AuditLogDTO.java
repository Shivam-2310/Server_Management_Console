package com.management.console.dto;

import com.management.console.domain.enums.ActionStatus;
import com.management.console.domain.enums.ServiceAction;
import lombok.*;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLogDTO {
    private Long id;
    private Long serviceId;
    private String serviceName;
    private LocalDateTime timestamp;
    private String username;
    private String userRole;
    private String ipAddress;
    private ServiceAction action;
    private String actionDetails;
    private Long durationMs;
    private String reason;
    private Boolean isAutomated;
    private ActionStatus status;
    private String resultMessage;
    private Boolean aiRecommended;
    private String aiRecommendation;
    private Integer riskLevel;
}

