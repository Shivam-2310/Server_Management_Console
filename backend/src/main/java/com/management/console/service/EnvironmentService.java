package com.management.console.service;

import com.management.console.domain.entity.ManagedService;
import com.management.console.exception.ResourceNotFoundException;
import com.management.console.repository.ManagedServiceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.Environment;
import org.springframework.core.env.PropertySource;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Service for viewing and managing environment variables and configuration properties.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EnvironmentService {

    private final Environment environment;
    private final ManagedServiceRepository serviceRepository;
    private final WebClient webClient;

    // Sensitive property patterns to mask
    private static final List<String> SENSITIVE_PATTERNS = Arrays.asList(
            "password", "secret", "key", "token", "credential", "auth",
            "private", "api-key", "apikey", "api_key", "jdbc"
    );

    /**
     * Get all environment properties for local application
     */
    public EnvironmentInfo getLocalEnvironment() {
        EnvironmentInfo info = new EnvironmentInfo();
        info.setTimestamp(System.currentTimeMillis());
        info.setActiveProfiles(Arrays.asList(environment.getActiveProfiles()));
        info.setDefaultProfiles(Arrays.asList(environment.getDefaultProfiles()));

        if (environment instanceof ConfigurableEnvironment) {
            ConfigurableEnvironment configEnv = (ConfigurableEnvironment) environment;
            
            List<PropertySourceInfo> propertySources = new ArrayList<>();
            for (PropertySource<?> source : configEnv.getPropertySources()) {
                PropertySourceInfo sourceInfo = new PropertySourceInfo();
                sourceInfo.setName(source.getName());
                sourceInfo.setType(source.getClass().getSimpleName());
                
                if (source instanceof EnumerablePropertySource) {
                    EnumerablePropertySource<?> enumSource = (EnumerablePropertySource<?>) source;
                    Map<String, String> properties = new LinkedHashMap<>();
                    
                    for (String propertyName : enumSource.getPropertyNames()) {
                        Object value = enumSource.getProperty(propertyName);
                        String stringValue = value != null ? value.toString() : null;
                        properties.put(propertyName, maskSensitiveValue(propertyName, stringValue));
                    }
                    sourceInfo.setProperties(properties);
                    sourceInfo.setPropertyCount(properties.size());
                }
                propertySources.add(sourceInfo);
            }
            info.setPropertySources(propertySources);
        }

        // System environment
        Map<String, String> systemEnv = new LinkedHashMap<>();
        System.getenv().forEach((key, value) -> 
            systemEnv.put(key, maskSensitiveValue(key, value)));
        info.setSystemEnvironment(systemEnv);

        // System properties
        Map<String, String> systemProps = new LinkedHashMap<>();
        System.getProperties().forEach((key, value) -> 
            systemProps.put(key.toString(), maskSensitiveValue(key.toString(), value.toString())));
        info.setSystemProperties(systemProps);

        return info;
    }

    /**
     * Get environment from remote service via actuator
     */
    public Mono<EnvironmentInfo> getRemoteEnvironment(Long serviceId) {
        ManagedService service = getService(serviceId);
        String actuatorUrl = buildActuatorUrl(service, "/env");

        return webClient.get()
                .uri(actuatorUrl)
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(Duration.ofSeconds(15))
                .map(this::parseEnvironmentResponse)
                .onErrorResume(e -> {
                    log.warn("Failed to fetch environment from {}: {}", service.getName(), e.getMessage());
                    return Mono.just(new EnvironmentInfo());
                });
    }

    /**
     * Get a specific property value
     */
    public PropertyValue getProperty(String propertyName) {
        PropertyValue pv = new PropertyValue();
        pv.setName(propertyName);
        pv.setValue(maskSensitiveValue(propertyName, environment.getProperty(propertyName)));
        pv.setResolved(environment.containsProperty(propertyName));
        
        // Try to find which property source contains it
        if (environment instanceof ConfigurableEnvironment) {
            ConfigurableEnvironment configEnv = (ConfigurableEnvironment) environment;
            for (PropertySource<?> source : configEnv.getPropertySources()) {
                if (source.containsProperty(propertyName)) {
                    pv.setSource(source.getName());
                    break;
                }
            }
        }
        
        return pv;
    }

    /**
     * Search properties by name pattern
     */
    public List<PropertyValue> searchProperties(String pattern) {
        List<PropertyValue> results = new ArrayList<>();
        String lowerPattern = pattern.toLowerCase();

        if (environment instanceof ConfigurableEnvironment) {
            ConfigurableEnvironment configEnv = (ConfigurableEnvironment) environment;
            Set<String> matchedProps = new HashSet<>();

            for (PropertySource<?> source : configEnv.getPropertySources()) {
                if (source instanceof EnumerablePropertySource) {
                    EnumerablePropertySource<?> enumSource = (EnumerablePropertySource<?>) source;
                    for (String propertyName : enumSource.getPropertyNames()) {
                        if (propertyName.toLowerCase().contains(lowerPattern) && !matchedProps.contains(propertyName)) {
                            PropertyValue pv = new PropertyValue();
                            pv.setName(propertyName);
                            Object value = enumSource.getProperty(propertyName);
                            pv.setValue(maskSensitiveValue(propertyName, value != null ? value.toString() : null));
                            pv.setSource(source.getName());
                            pv.setResolved(true);
                            results.add(pv);
                            matchedProps.add(propertyName);
                        }
                    }
                }
            }
        }

        return results.stream()
                .sorted(Comparator.comparing(PropertyValue::getName))
                .limit(100)
                .collect(Collectors.toList());
    }

    /**
     * Get configuration metadata (beans, context info)
     */
    public ConfigurationInfo getConfigurationInfo() {
        ConfigurationInfo info = new ConfigurationInfo();
        info.setTimestamp(System.currentTimeMillis());
        info.setActiveProfiles(Arrays.asList(environment.getActiveProfiles()));
        
        // Get commonly queried properties
        Map<String, String> commonProperties = new LinkedHashMap<>();
        List<String> commonPropNames = Arrays.asList(
                "server.port", "spring.application.name", "spring.profiles.active",
                "spring.datasource.url", "logging.level.root", "management.endpoints.web.exposure.include"
        );
        
        for (String propName : commonPropNames) {
            String value = environment.getProperty(propName);
            if (value != null) {
                commonProperties.put(propName, maskSensitiveValue(propName, value));
            }
        }
        info.setCommonProperties(commonProperties);

        return info;
    }

    /**
     * Get property sources summary
     */
    public List<PropertySourceSummary> getPropertySourcesSummary() {
        List<PropertySourceSummary> summaries = new ArrayList<>();

        if (environment instanceof ConfigurableEnvironment) {
            ConfigurableEnvironment configEnv = (ConfigurableEnvironment) environment;
            int priority = 0;
            
            for (PropertySource<?> source : configEnv.getPropertySources()) {
                PropertySourceSummary summary = new PropertySourceSummary();
                summary.setName(source.getName());
                summary.setType(source.getClass().getSimpleName());
                summary.setPriority(priority++);
                
                if (source instanceof EnumerablePropertySource) {
                    EnumerablePropertySource<?> enumSource = (EnumerablePropertySource<?>) source;
                    summary.setPropertyCount(enumSource.getPropertyNames().length);
                }
                
                summaries.add(summary);
            }
        }

        return summaries;
    }

    // Helper methods

    private ManagedService getService(Long serviceId) {
        return serviceRepository.findById(serviceId)
                .orElseThrow(() -> new ResourceNotFoundException("Service not found: " + serviceId));
    }

    private String buildActuatorUrl(ManagedService service, String endpoint) {
        String baseUrl = service.getActuatorUrl();
        if (baseUrl == null || baseUrl.isEmpty()) {
            baseUrl = String.format("http://%s:%d/actuator",
                    service.getHost() != null ? service.getHost() : "localhost",
                    service.getPort() != null ? service.getPort() : 8080);
        }
        return baseUrl + endpoint;
    }

    private String maskSensitiveValue(String propertyName, String value) {
        if (value == null) return null;
        
        String lowerName = propertyName.toLowerCase();
        for (String pattern : SENSITIVE_PATTERNS) {
            if (lowerName.contains(pattern)) {
                return "******";
            }
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    private EnvironmentInfo parseEnvironmentResponse(Map<String, Object> response) {
        EnvironmentInfo info = new EnvironmentInfo();
        info.setTimestamp(System.currentTimeMillis());

        List<String> profiles = (List<String>) response.get("activeProfiles");
        if (profiles != null) {
            info.setActiveProfiles(profiles);
        }

        List<Map<String, Object>> propertySources = (List<Map<String, Object>>) response.get("propertySources");
        if (propertySources != null) {
            List<PropertySourceInfo> sourceInfos = new ArrayList<>();
            for (Map<String, Object> source : propertySources) {
                PropertySourceInfo sourceInfo = new PropertySourceInfo();
                sourceInfo.setName((String) source.get("name"));
                
                Map<String, Object> properties = (Map<String, Object>) source.get("properties");
                if (properties != null) {
                    Map<String, String> propMap = new LinkedHashMap<>();
                    properties.forEach((key, val) -> {
                        if (val instanceof Map) {
                            Map<String, Object> propValue = (Map<String, Object>) val;
                            propMap.put(key, maskSensitiveValue(key, (String) propValue.get("value")));
                        }
                    });
                    sourceInfo.setProperties(propMap);
                    sourceInfo.setPropertyCount(propMap.size());
                }
                sourceInfos.add(sourceInfo);
            }
            info.setPropertySources(sourceInfos);
        }

        return info;
    }

    // DTOs

    @lombok.Data
    public static class EnvironmentInfo {
        private long timestamp;
        private List<String> activeProfiles;
        private List<String> defaultProfiles;
        private List<PropertySourceInfo> propertySources;
        private Map<String, String> systemEnvironment;
        private Map<String, String> systemProperties;
    }

    @lombok.Data
    public static class PropertySourceInfo {
        private String name;
        private String type;
        private int propertyCount;
        private Map<String, String> properties;
    }

    @lombok.Data
    public static class PropertyValue {
        private String name;
        private String value;
        private String source;
        private boolean resolved;
    }

    @lombok.Data
    public static class ConfigurationInfo {
        private long timestamp;
        private List<String> activeProfiles;
        private Map<String, String> commonProperties;
    }

    @lombok.Data
    public static class PropertySourceSummary {
        private String name;
        private String type;
        private int priority;
        private int propertyCount;
    }
}

