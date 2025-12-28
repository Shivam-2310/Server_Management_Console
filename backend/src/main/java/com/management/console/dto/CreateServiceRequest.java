package com.management.console.dto;

import com.management.console.domain.enums.ServiceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.*;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateServiceRequest {
    
    @NotBlank(message = "Service name is required")
    private String name;
    
    private String description;
    
    @NotNull(message = "Service type is required")
    private ServiceType serviceType;
    
    @NotBlank(message = "Host is required")
    private String host;
    
    @NotNull(message = "Port is required")
    @Positive(message = "Port must be positive")
    private Integer port;
    
    private String healthEndpoint;
    private String metricsEndpoint;
    private String baseUrl;
    
    // For Spring Boot services
    private String actuatorBasePath;
    
    // For Frontend services
    private String frontendTechnology;
    private String servingTechnology;
    
    // Lifecycle commands
    private String startCommand;
    private String stopCommand;
    private String restartCommand;
    private String workingDirectory;
    private String processIdentifier;
    
    private List<String> tags;
    private String environment;
}

