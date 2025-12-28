package com.management.console.service;

import com.management.console.domain.entity.ManagedService;
import com.management.console.domain.enums.HealthStatus;
import com.management.console.domain.enums.ServiceType;
import com.management.console.dto.CreateServiceRequest;
import com.management.console.dto.ServiceDTO;
import com.management.console.dto.UpdateServiceRequest;
import com.management.console.exception.ResourceNotFoundException;
import com.management.console.exception.DuplicateResourceException;
import com.management.console.mapper.ServiceMapper;
import com.management.console.repository.ManagedServiceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ServiceRegistryService {

    private final ManagedServiceRepository serviceRepository;
    private final ServiceMapper serviceMapper;

    public ServiceDTO registerService(CreateServiceRequest request) {
        log.info("Registering new service: {}", request.getName());
        log.debug("Request details - Name: {}, Type: {}, Host: {}, Port: {}", 
                request.getName(), request.getServiceType(), request.getHost(), request.getPort());
        
        // Validate required fields
        if (request.getName() == null || request.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Service name is required");
        }
        if (request.getServiceType() == null) {
            throw new IllegalArgumentException("Service type is required");
        }
        if (request.getHost() == null || request.getHost().trim().isEmpty()) {
            throw new IllegalArgumentException("Host is required");
        }
        if (request.getPort() == null || request.getPort() <= 0) {
            throw new IllegalArgumentException("Port must be a positive number");
        }
        
        if (serviceRepository.existsByName(request.getName().trim())) {
            throw new DuplicateResourceException("Service with name '" + request.getName() + "' already exists");
        }

        // Initialize tags list - ensure it's never null
        List<String> tags = request.getTags() != null && !request.getTags().isEmpty() 
                ? request.getTags() 
                : new ArrayList<>();
        
        ManagedService service = ManagedService.builder()
                .name(request.getName().trim())
                .description(nullIfEmpty(request.getDescription()))
                .serviceType(request.getServiceType())
                .healthStatus(HealthStatus.UNKNOWN)
                .host(request.getHost().trim())
                .port(request.getPort())
                .healthEndpoint(nullIfEmpty(request.getHealthEndpoint()))
                .metricsEndpoint(nullIfEmpty(request.getMetricsEndpoint()))
                .baseUrl(nullIfEmpty(request.getBaseUrl()))
                .actuatorBasePath(nullIfEmpty(request.getActuatorBasePath()))
                .frontendTechnology(nullIfEmpty(request.getFrontendTechnology()))
                .servingTechnology(nullIfEmpty(request.getServingTechnology()))
                .startCommand(nullIfEmpty(request.getStartCommand()))
                .stopCommand(nullIfEmpty(request.getStopCommand()))
                .restartCommand(nullIfEmpty(request.getRestartCommand()))
                .workingDirectory(nullIfEmpty(request.getWorkingDirectory()))
                .processIdentifier(nullIfEmpty(request.getProcessIdentifier()))
                .tags(tags)
                .environment(nullIfEmpty(request.getEnvironment()))
                .enabled(true)
                .isRunning(false)
                .instanceCount(1)
                .stabilityScore(100)
                .riskScore(0)
                .build();

        // Ensure createdAt is set (in case @PrePersist doesn't fire)
        if (service.getCreatedAt() == null) {
            service.setCreatedAt(LocalDateTime.now());
        }
        if (service.getUpdatedAt() == null) {
            service.setUpdatedAt(LocalDateTime.now());
        }

        try {
            ManagedService saved = serviceRepository.save(service);
            log.info("Service registered successfully with ID: {}", saved.getId());
            
            return serviceMapper.toDTO(saved);
        } catch (Exception e) {
            log.error("Failed to register service: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to register service: " + e.getMessage(), e);
        }
    }

    public ServiceDTO updateService(Long id, UpdateServiceRequest request) {
        log.info("Updating service with ID: {}", id);
        
        ManagedService service = getServiceEntity(id);

        if (request.getName() != null && !request.getName().equals(service.getName())) {
            if (serviceRepository.existsByName(request.getName())) {
                throw new DuplicateResourceException("Service with name '" + request.getName() + "' already exists");
            }
            service.setName(request.getName());
        }

        if (request.getDescription() != null) service.setDescription(request.getDescription());
        if (request.getServiceType() != null) service.setServiceType(request.getServiceType());
        if (request.getHost() != null) service.setHost(request.getHost());
        if (request.getPort() != null) service.setPort(request.getPort());
        if (request.getHealthEndpoint() != null) service.setHealthEndpoint(request.getHealthEndpoint());
        if (request.getMetricsEndpoint() != null) service.setMetricsEndpoint(request.getMetricsEndpoint());
        if (request.getBaseUrl() != null) service.setBaseUrl(request.getBaseUrl());
        if (request.getActuatorBasePath() != null) service.setActuatorBasePath(request.getActuatorBasePath());
        if (request.getFrontendTechnology() != null) service.setFrontendTechnology(request.getFrontendTechnology());
        if (request.getServingTechnology() != null) service.setServingTechnology(request.getServingTechnology());
        if (request.getStartCommand() != null) service.setStartCommand(request.getStartCommand());
        if (request.getStopCommand() != null) service.setStopCommand(request.getStopCommand());
        if (request.getRestartCommand() != null) service.setRestartCommand(request.getRestartCommand());
        if (request.getWorkingDirectory() != null) service.setWorkingDirectory(request.getWorkingDirectory());
        if (request.getProcessIdentifier() != null) service.setProcessIdentifier(request.getProcessIdentifier());
        if (request.getTags() != null) service.setTags(request.getTags());
        if (request.getEnvironment() != null) service.setEnvironment(request.getEnvironment());
        if (request.getEnabled() != null) service.setEnabled(request.getEnabled());

        ManagedService saved = serviceRepository.save(service);
        log.info("Service updated successfully: {}", saved.getName());
        
        return serviceMapper.toDTO(saved);
    }

    @Transactional(readOnly = true)
    public ServiceDTO getService(Long id) {
        return serviceMapper.toDTO(getServiceEntity(id));
    }

    @Transactional(readOnly = true)
    public ServiceDTO getServiceByName(String name) {
        ManagedService service = serviceRepository.findByName(name)
                .orElseThrow(() -> new ResourceNotFoundException("Service not found with name: " + name));
        return serviceMapper.toDTO(service);
    }

    @Transactional(readOnly = true)
    public List<ServiceDTO> getAllServices() {
        return serviceRepository.findAll().stream()
                .map(serviceMapper::toDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ServiceDTO> getEnabledServices() {
        return serviceRepository.findByEnabledTrue().stream()
                .map(serviceMapper::toDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ServiceDTO> getServicesByType(ServiceType type) {
        return serviceRepository.findByServiceType(type).stream()
                .map(serviceMapper::toDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ServiceDTO> getServicesByHealth(HealthStatus status) {
        return serviceRepository.findByHealthStatus(status).stream()
                .map(serviceMapper::toDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ServiceDTO> getServicesNeedingHealthCheck(int thresholdSeconds) {
        LocalDateTime threshold = LocalDateTime.now().minusSeconds(thresholdSeconds);
        return serviceRepository.findServicesNeedingHealthCheck(threshold).stream()
                .map(serviceMapper::toDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ServiceDTO> getHighRiskServices(int riskThreshold) {
        return serviceRepository.findServicesWithHighRisk(riskThreshold).stream()
                .map(serviceMapper::toDTO)
                .collect(Collectors.toList());
    }

    public void deleteService(Long id) {
        log.info("Deleting service with ID: {}", id);
        ManagedService service = getServiceEntity(id);
        serviceRepository.delete(service);
        log.info("Service deleted: {}", service.getName());
    }

    public void updateHealthStatus(Long serviceId, HealthStatus status) {
        ManagedService service = getServiceEntity(serviceId);
        service.setHealthStatus(status);
        service.setLastHealthCheck(LocalDateTime.now());
        serviceRepository.save(service);
    }

    public void updateMetricsSnapshot(Long serviceId, Double cpu, Double memory, Long responseTime, Double errorRate) {
        ManagedService service = getServiceEntity(serviceId);
        service.setCpuUsage(cpu);
        service.setMemoryUsage(memory);
        service.setResponseTime(responseTime);
        service.setErrorRate(errorRate);
        service.setLastMetricsCollection(LocalDateTime.now());
        serviceRepository.save(service);
    }

    public void updateRiskScores(Long serviceId, Integer stabilityScore, Integer riskScore, String riskTrend) {
        ManagedService service = getServiceEntity(serviceId);
        service.setStabilityScore(stabilityScore);
        service.setRiskScore(riskScore);
        service.setRiskTrend(riskTrend);
        serviceRepository.save(service);
    }

    public void updateRunningState(Long serviceId, Boolean isRunning) {
        ManagedService service = getServiceEntity(serviceId);
        service.setIsRunning(isRunning);
        if (isRunning) {
            service.setLastRestart(LocalDateTime.now());
        }
        serviceRepository.save(service);
    }

    @Transactional(readOnly = true)
    public Map<String, Long> getHealthDistribution() {
        List<Object[]> results = serviceRepository.getHealthDistribution();
        Map<String, Long> distribution = new HashMap<>();
        for (Object[] result : results) {
            distribution.put(((HealthStatus) result[0]).name(), (Long) result[1]);
        }
        return distribution;
    }

    @Transactional(readOnly = true)
    public Map<String, Long> getServiceTypeDistribution() {
        List<Object[]> results = serviceRepository.countByServiceType();
        Map<String, Long> distribution = new HashMap<>();
        for (Object[] result : results) {
            distribution.put(((ServiceType) result[0]).name(), (Long) result[1]);
        }
        return distribution;
    }

    public ManagedService getServiceEntity(Long id) {
        return serviceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Service not found with ID: " + id));
    }

    /**
     * Helper method to convert empty strings to null for optional fields
     */
    private String nullIfEmpty(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}

