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
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for viewing and managing environment variables and configuration properties.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EnvironmentService {

    private final Environment environment;
    private final ManagedServiceRepository serviceRepository;
    private final WebClient.Builder webClientBuilder;

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
     * Follows Spring Boot Admin's approach to configuration retrieval
     */
    @SuppressWarnings("unchecked")
    public Mono<EnvironmentInfo> getRemoteEnvironment(Long serviceId) {
        ManagedService service = getService(serviceId);
        
        // Validate service configuration
        if (service.getHost() == null || service.getPort() == null) {
            log.error("Service {} is missing host or port configuration", service.getName());
            EnvironmentInfo emptyInfo = new EnvironmentInfo();
            emptyInfo.setTimestamp(System.currentTimeMillis());
            emptyInfo.setActiveProfiles(new ArrayList<>());
            emptyInfo.setPropertySources(new ArrayList<>());
            return Mono.just(emptyInfo);
        }
        
        String actuatorUrl = buildActuatorUrl(service, "/env");

        log.info("=== STEP 1: Fetching environment configuration ===");
        log.info("Service: {} (ID: {})", service.getName(), service.getId());
        log.info("Host: {}, Port: {}", service.getHost(), service.getPort());
        log.info("Actuator URL: {}", actuatorUrl);

        // Set explicit buffer limit for large env responses (unlimited)
        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(-1)) // Unlimited - maximum memory
                .build();

        // Build WebClient without baseUrl to allow full URLs
        WebClient webClient = WebClient.builder()
                .exchangeStrategies(strategies) // Explicitly set buffer limit
                .build();

        return webClient.get()
                .uri(actuatorUrl)
                .retrieve()
                .bodyToMono(Map.class)
                .cast(Map.class)
                .map(m -> (Map<String, Object>) m)
                .timeout(Duration.ofSeconds(30)) // Increased timeout for large env responses
                .doOnNext(response -> {
                    log.info("=== STEP 3: Received env response from {} ===", service.getName());
                    log.info("Response size: {} keys", response != null ? response.size() : 0);
                    if (response != null && !response.isEmpty()) {
                        log.info("Env response keys: {}", response.keySet());
                        log.info("Has propertySources: {}", response.containsKey("propertySources"));
                    } else {
                        log.warn("WARNING: Empty or null response received from {}", service.getName());
                    }
                })
                .doOnError(error -> {
                    log.error("=== ERROR in WebClient request for environment ===");
                    log.error("Service: {} (ID: {})", service.getName(), service.getId());
                    log.error("URL: {}", actuatorUrl);
                    log.error("Error type: {}", error.getClass().getName());
                    log.error("Error message: {}", error.getMessage());
                    if (error.getCause() != null) {
                        log.error("Caused by: {} - {}", error.getCause().getClass().getName(), error.getCause().getMessage());
                    }
                    log.error("Full stack trace:", error);
                })
                .map(response -> {
                    log.info("=== STEP 4: Parsing environment response ===");
                    EnvironmentInfo info = parseEnvironmentResponse(response);
                    log.info("Parsed environment from {}: {} property sources, {} total properties", 
                            service.getName(), 
                            info.getPropertySources() != null ? info.getPropertySources().size() : 0,
                            info.getPropertySources() != null ? 
                                    info.getPropertySources().stream()
                                            .mapToInt(PropertySourceInfo::getPropertyCount)
                                            .sum() : 0);
                    return info;
                })
                .onErrorResume(e -> {
                    log.error("=== ERROR: Failed to fetch environment (onErrorResume) ===");
                    log.error("Service: {} (ID: {})", service.getName(), service.getId());
                    log.error("URL: {}", actuatorUrl);
                    log.error("Error type: {}", e.getClass().getName());
                    log.error("Error message: {}", e.getMessage());
                    if (e.getCause() != null) {
                        log.error("Caused by: {} - {}", e.getCause().getClass().getName(), e.getCause().getMessage());
                    }
                    log.error("Full stack trace:", e);
                    // Return empty environment info instead of failing
                    EnvironmentInfo emptyInfo = new EnvironmentInfo();
                    emptyInfo.setTimestamp(System.currentTimeMillis());
                    emptyInfo.setActiveProfiles(new ArrayList<>());
                    emptyInfo.setPropertySources(new ArrayList<>());
                    return Mono.just(emptyInfo);
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
        // Ensure baseUrl doesn't end with / and endpoint starts with /
        if (baseUrl.endsWith("/") && endpoint.startsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        } else if (!baseUrl.endsWith("/") && !endpoint.startsWith("/")) {
            baseUrl = baseUrl + "/";
        }
        String fullUrl = baseUrl + endpoint;
        log.debug("Built actuator URL: {} (baseUrl: {}, endpoint: {})", fullUrl, baseUrl, endpoint);
        return fullUrl;
    }

    private String maskSensitiveValue(String propertyName, String value) {
        if (value == null) return null;
        
        // If value is already masked by actuator (starts with **** or is [MASKED]), return as-is
        if (value.startsWith("****") || value.equals("[MASKED]")) {
            return value;
        }
        
        // Only mask truly sensitive values (passwords, secrets, tokens, credentials)
        // For application configuration display, show most values unmasked
        String lowerName = propertyName.toLowerCase();
        
        // Check for sensitive patterns - only mask if it's clearly a sensitive property
        // Be more specific to avoid false positives
        if ((lowerName.contains("password") && !lowerName.contains("path")) || 
            (lowerName.contains("secret") && !lowerName.contains("secretary")) || 
            (lowerName.contains("token") && !lowerName.contains("timeout")) || 
            lowerName.contains("credential") ||
            lowerName.contains("api-key") || 
            lowerName.contains("apikey") ||
            lowerName.contains("private-key") ||
            lowerName.contains("privatekey") ||
            (lowerName.contains("key") && (lowerName.contains("jwt") || lowerName.contains("auth")))) {
            return "******";
        }
        
        // Return unmasked value for all other properties
        return value;
    }
    
    /**
     * Check if a property source is application-related (not system)
     */
    private boolean isApplicationPropertySource(String sourceName) {
        if (sourceName == null) return false;
        String lowerName = sourceName.toLowerCase();
        // Include application properties, YAML files, and configuration classes
        // Exclude system properties, system environment, and JVM properties
        // Also exclude servlet context params and command line args that are system-level
        boolean isApplication = lowerName.contains("application") || 
               lowerName.contains("properties") ||
               lowerName.contains("yaml") ||
               lowerName.contains("yml") ||
               (lowerName.contains("config") && !lowerName.contains("system"));
        
        boolean isSystem = lowerName.contains("systemproperties") ||
                lowerName.contains("systemenvironment") ||
                lowerName.contains("servletcontextinitparams") ||
                lowerName.contains("servletconfiginitparams") ||
                (lowerName.contains("commandline") && !lowerName.contains("application"));
        
        return isApplication && !isSystem;
    }

    @SuppressWarnings("unchecked")
    private EnvironmentInfo parseEnvironmentResponse(Map<String, Object> response) {
        EnvironmentInfo info = new EnvironmentInfo();
        info.setTimestamp(System.currentTimeMillis());

        if (response == null || response.isEmpty()) {
            log.debug("Empty environment response received");
            info.setActiveProfiles(new ArrayList<>());
            info.setPropertySources(new ArrayList<>());
            return info;
        }

        // Parse active profiles
        Object profilesObj = response.get("activeProfiles");
        if (profilesObj instanceof List) {
            info.setActiveProfiles((List<String>) profilesObj);
        } else {
            info.setActiveProfiles(new ArrayList<>());
        }

        // Parse default profiles
        Object defaultProfilesObj = response.get("defaultProfiles");
        if (defaultProfilesObj instanceof List) {
            info.setDefaultProfiles((List<String>) defaultProfilesObj);
        } else {
            info.setDefaultProfiles(new ArrayList<>());
        }

        // Parse property sources - filter to show only application configuration
        Object propertySourcesObj = response.get("propertySources");
        List<PropertySourceInfo> sourceInfos = new ArrayList<>();
        
        if (propertySourcesObj instanceof List) {
            List<Map<String, Object>> propertySources = (List<Map<String, Object>>) propertySourcesObj;
            for (Map<String, Object> source : propertySources) {
                String sourceName = (String) source.get("name");
                
                // Skip system properties, system environment, and servlet context params
                // Only show application configuration
                if (!isApplicationPropertySource(sourceName)) {
                    log.debug("Skipping non-application property source: {}", sourceName);
                    continue;
                }
                
                PropertySourceInfo sourceInfo = new PropertySourceInfo();
                sourceInfo.setName(sourceName);
                
                // Determine type based on name
                if (sourceName != null) {
                    if (sourceName.contains("application")) {
                        sourceInfo.setType("Application Properties");
                    } else if (sourceName.contains("bootstrap")) {
                        sourceInfo.setType("Bootstrap Properties");
                    } else if (sourceName.contains("config")) {
                        sourceInfo.setType("Configuration");
                    } else {
                        sourceInfo.setType("Application Configuration");
                    }
                }
                
                Object propertiesObj = source.get("properties");
                if (propertiesObj instanceof Map) {
                    Map<String, Object> properties = (Map<String, Object>) propertiesObj;
                    Map<String, String> propMap = new LinkedHashMap<>();
                    properties.forEach((key, val) -> {
                        if (val instanceof Map) {
                            Map<String, Object> propValue = (Map<String, Object>) val;
                            Object valueObj = propValue.get("value");
                            String value = valueObj != null ? valueObj.toString() : null;
                            // Show unmasked values for application configuration (only mask truly sensitive)
                            propMap.put(key, maskSensitiveValue(key, value));
                        } else if (val != null) {
                            // Handle direct value (not wrapped in map)
                            propMap.put(key, maskSensitiveValue(key, val.toString()));
                        }
                    });
                    sourceInfo.setProperties(propMap);
                    sourceInfo.setPropertyCount(propMap.size());
                } else {
                    sourceInfo.setProperties(new LinkedHashMap<>());
                    sourceInfo.setPropertyCount(0);
                }
                sourceInfos.add(sourceInfo);
            }
        }
        
        info.setPropertySources(sourceInfos);
        // Don't include system properties and system environment - only application config
        info.setSystemEnvironment(new LinkedHashMap<>());
        info.setSystemProperties(new LinkedHashMap<>());
        
        log.debug("Parsed {} application property sources with total {} properties", 
                sourceInfos.size(), 
                sourceInfos.stream().mapToInt(PropertySourceInfo::getPropertyCount).sum());

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

