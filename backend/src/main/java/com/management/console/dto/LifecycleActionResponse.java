package com.management.console.dto;

import com.management.console.domain.enums.ActionStatus;
import com.management.console.domain.enums.ServiceAction;
import lombok.*;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LifecycleActionResponse {
    private Long actionId;
    private Long serviceId;
    private String serviceName;
    private ServiceAction action;
    private ActionStatus status;
    private String message;
    private LocalDateTime timestamp;
    private Long durationMs;
    private Boolean requiresConfirmation;
    private Integer riskLevel;
    private String aiRecommendation;
}

