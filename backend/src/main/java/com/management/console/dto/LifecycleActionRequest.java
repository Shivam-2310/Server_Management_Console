package com.management.console.dto;

import com.management.console.domain.enums.ServiceAction;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LifecycleActionRequest {
    
    @NotNull(message = "Service ID is required")
    private Long serviceId;
    
    @NotNull(message = "Action is required")
    private ServiceAction action;
    
    private String reason;
    
    private Boolean dryRun;
    
    private Boolean confirmed;
    
    // For scale actions
    private Integer targetInstances;
    
    // For log level change
    private String loggerName;
    private String logLevel;
}

