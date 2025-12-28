package com.management.console.dto;

import com.management.console.domain.enums.ServiceType;
import lombok.*;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateServiceRequest {
    private String name;
    private String description;
    private ServiceType serviceType;
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
    private List<String> tags;
    private String environment;
    private Boolean enabled;
}

