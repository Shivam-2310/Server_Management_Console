package com.management.console.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import com.management.console.domain.entity.ManagedService;
import com.management.console.exception.ResourceNotFoundException;
import com.management.console.repository.ManagedServiceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for managing logger levels dynamically at runtime.
 * Can manage both local loggers and remote service loggers via actuator.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LoggerManagementService {

    private final ManagedServiceRepository serviceRepository;
    private final WebClient webClient;

    // Track logger level changes for audit
    private final Map<String, List<LoggerChange>> changeHistory = new LinkedHashMap<>();

    /**
     * Get all loggers for local application
     */
    public List<LoggerInfo> getLocalLoggers() {
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        
        return loggerContext.getLoggerList().stream()
                .map(logger -> {
                    LoggerInfo info = new LoggerInfo();
                    info.setName(logger.getName());
                    info.setEffectiveLevel(logger.getEffectiveLevel().toString());
                    info.setConfiguredLevel(logger.getLevel() != null ? logger.getLevel().toString() : null);
                    return info;
                })
                .sorted(Comparator.comparing(LoggerInfo::getName))
                .collect(Collectors.toList());
    }

    /**
     * Get loggers from a remote service via actuator
     */
    @SuppressWarnings("unchecked")
    public Mono<List<LoggerInfo>> getRemoteLoggers(Long serviceId) {
        ManagedService service = getService(serviceId);
        String actuatorUrl = buildActuatorUrl(service, "/loggers");

        return webClient.get()
                .uri(actuatorUrl)
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(Duration.ofSeconds(10))
                .map(response -> parseLoggersResponse((Map<String, Object>) response))
                .onErrorResume(e -> {
                    log.warn("Failed to fetch loggers from {}: {}", service.getName(), e.getMessage());
                    return Mono.just(new ArrayList<>());
                });
    }

    /**
     * Set logger level locally
     */
    public LoggerChangeResult setLocalLoggerLevel(String loggerName, String level) {
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        Logger logger = loggerContext.getLogger(loggerName);

        if (logger == null) {
            return LoggerChangeResult.builder()
                    .success(false)
                    .message("Logger not found: " + loggerName)
                    .build();
        }

        String oldLevel = logger.getLevel() != null ? logger.getLevel().toString() : "INHERITED";

        try {
            if (level == null || level.isEmpty() || "INHERITED".equalsIgnoreCase(level)) {
                logger.setLevel(null);
            } else {
                logger.setLevel(Level.valueOf(level.toUpperCase()));
            }

            String newLevel = logger.getLevel() != null ? logger.getLevel().toString() : "INHERITED";

            // Track change
            LoggerChange change = new LoggerChange();
            change.setLoggerName(loggerName);
            change.setOldLevel(oldLevel);
            change.setNewLevel(newLevel);
            change.setTimestamp(System.currentTimeMillis());
            change.setServiceName("local");

            changeHistory.computeIfAbsent(loggerName, k -> new ArrayList<>()).add(change);

            log.info("Logger level changed: {} from {} to {}", loggerName, oldLevel, newLevel);

            return LoggerChangeResult.builder()
                    .success(true)
                    .loggerName(loggerName)
                    .oldLevel(oldLevel)
                    .newLevel(newLevel)
                    .effectiveLevel(logger.getEffectiveLevel().toString())
                    .message("Logger level updated successfully")
                    .build();

        } catch (Exception e) {
            log.error("Failed to set logger level for {}: {}", loggerName, e.getMessage());
            return LoggerChangeResult.builder()
                    .success(false)
                    .message("Failed to set logger level: " + e.getMessage())
                    .build();
        }
    }

    /**
     * Set logger level on a remote service via actuator
     */
    public Mono<LoggerChangeResult> setRemoteLoggerLevel(Long serviceId, String loggerName, String level) {
        ManagedService service = getService(serviceId);
        String actuatorUrl = buildActuatorUrl(service, "/loggers/" + loggerName);

        Map<String, String> body = new HashMap<>();
        body.put("configuredLevel", level);

        return webClient.post()
                .uri(actuatorUrl)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Void.class)
                .timeout(Duration.ofSeconds(10))
                .then(Mono.fromCallable(() -> {
                    log.info("Logger level changed on {}: {} to {}", service.getName(), loggerName, level);
                    
                    LoggerChange change = new LoggerChange();
                    change.setLoggerName(loggerName);
                    change.setNewLevel(level);
                    change.setTimestamp(System.currentTimeMillis());
                    change.setServiceName(service.getName());
                    changeHistory.computeIfAbsent(loggerName, k -> new ArrayList<>()).add(change);

                    return LoggerChangeResult.builder()
                            .success(true)
                            .loggerName(loggerName)
                            .newLevel(level)
                            .message("Logger level updated on " + service.getName())
                            .build();
                }))
                .onErrorResume(e -> {
                    log.error("Failed to set logger level on {}: {}", service.getName(), e.getMessage());
                    return Mono.just(LoggerChangeResult.builder()
                            .success(false)
                            .message("Failed to set logger level: " + e.getMessage())
                            .build());
                });
    }

    /**
     * Reset logger to inherited level
     */
    public LoggerChangeResult resetLoggerLevel(String loggerName) {
        return setLocalLoggerLevel(loggerName, null);
    }

    /**
     * Get available log levels
     */
    public List<String> getAvailableLevels() {
        return Arrays.asList("TRACE", "DEBUG", "INFO", "WARN", "ERROR", "OFF", "INHERITED");
    }

    /**
     * Search loggers by name pattern
     */
    public List<LoggerInfo> searchLoggers(String pattern) {
        return getLocalLoggers().stream()
                .filter(logger -> logger.getName().toLowerCase().contains(pattern.toLowerCase()))
                .collect(Collectors.toList());
    }

    /**
     * Get logger change history
     */
    public List<LoggerChange> getChangeHistory(String loggerName) {
        return changeHistory.getOrDefault(loggerName, Collections.emptyList());
    }

    /**
     * Get all recent changes
     */
    public List<LoggerChange> getRecentChanges(int limit) {
        return changeHistory.values().stream()
                .flatMap(List::stream)
                .sorted(Comparator.comparingLong(LoggerChange::getTimestamp).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    /**
     * Bulk update logger levels
     */
    public List<LoggerChangeResult> bulkUpdateLevels(Map<String, String> loggerLevels) {
        return loggerLevels.entrySet().stream()
                .map(entry -> setLocalLoggerLevel(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList());
    }

    /**
     * Get loggers grouped by package
     */
    public Map<String, List<LoggerInfo>> getLoggersGroupedByPackage() {
        return getLocalLoggers().stream()
                .collect(Collectors.groupingBy(logger -> {
                    String name = logger.getName();
                    int lastDot = name.lastIndexOf('.');
                    return lastDot > 0 ? name.substring(0, lastDot) : "root";
                }));
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

    @SuppressWarnings("unchecked")
    private List<LoggerInfo> parseLoggersResponse(Map<String, Object> response) {
        List<LoggerInfo> loggers = new ArrayList<>();
        
        Map<String, Object> loggersMap = (Map<String, Object>) response.get("loggers");
        if (loggersMap != null) {
            loggersMap.forEach((name, value) -> {
                Map<String, String> loggerData = (Map<String, String>) value;
                LoggerInfo info = new LoggerInfo();
                info.setName(name);
                info.setEffectiveLevel(loggerData.get("effectiveLevel"));
                info.setConfiguredLevel(loggerData.get("configuredLevel"));
                loggers.add(info);
            });
        }
        
        return loggers.stream()
                .sorted(Comparator.comparing(LoggerInfo::getName))
                .collect(Collectors.toList());
    }

    // DTOs

    @lombok.Data
    public static class LoggerInfo {
        private String name;
        private String effectiveLevel;
        private String configuredLevel;
    }

    @lombok.Data
    @lombok.Builder
    public static class LoggerChangeResult {
        private boolean success;
        private String loggerName;
        private String oldLevel;
        private String newLevel;
        private String effectiveLevel;
        private String message;
    }

    @lombok.Data
    public static class LoggerChange {
        private String loggerName;
        private String oldLevel;
        private String newLevel;
        private long timestamp;
        private String serviceName;
        private String changedBy;
    }
}

