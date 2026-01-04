package com.management.console.service;

import com.management.console.domain.entity.ManagedService;
import com.management.console.exception.ResourceNotFoundException;
import com.management.console.repository.ManagedServiceRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Service for collecting logs from remote Spring Boot services via Actuator endpoints.
 * Logs are collected per service and per instance, ensuring strict isolation.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RemoteLogCollectionService {

    private final ManagedServiceRepository serviceRepository;
    private final WebClient.Builder webClientBuilder;
    private final ServiceLogManager serviceLogManager;

    private static final DateTimeFormatter LOG_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    // Spring Boot default log format: 2026-01-04 14:05:04 [http-nio-8500-exec-2] DEBUG o.s.web.servlet.DispatcherServlet - message
    private static final Pattern SPRING_BOOT_LOG_PATTERN = Pattern.compile(
        "^(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2})\\s+\\[(.*?)\\]\\s+(\\w+)\\s+(.*?)\\s+-\\s+(.*)$"
    );
    // Alternative format: [2024-01-01 12:00:00.000] LEVEL logger - message
    private static final Pattern BRACKET_LOG_PATTERN = Pattern.compile(
        "^\\[(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3})\\]\\s+(\\w+)\\s+(.*?)\\s+-\\s+(.*)$"
    );

    /**
     * Collect logs from a remote service via Actuator logfile endpoint.
     * Logs are isolated per service and per instance.
     * Follows Spring Boot Admin's approach to log collection.
     */
    public Mono<List<RemoteLogEntry>> collectLogsFromService(Long serviceId, Integer lines, String level) {
        ManagedService service = getService(serviceId);
        
        if (service.getServiceType() != com.management.console.domain.enums.ServiceType.BACKEND) {
            log.warn("Log collection only supported for BACKEND services. Service {} is {}", 
                    service.getName(), service.getServiceType());
            return Mono.just(new ArrayList<>());
        }

        // Validate service configuration
        if (service.getHost() == null || service.getPort() == null) {
            log.error("Service {} is missing host or port configuration", service.getName());
            return Mono.just(new ArrayList<>());
        }

        String actuatorUrl = buildActuatorUrl(service, "/logfile");
        log.info("=== STEP 0: Service validation ===");
        log.info("Service: {} (ID: {})", service.getName(), service.getId());
        log.info("Host: {}, Port: {}", service.getHost(), service.getPort());
        log.info("Actuator URL: {}", actuatorUrl);
        
        // CRITICAL: Create WebClient with explicit buffer limit for large logfiles (unlimited)
        // We must set ExchangeStrategies explicitly to ensure buffer limit is applied
        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(-1)) // Unlimited - maximum memory
                .build();
        
        // Build WebClient without baseUrl to allow full URLs
        WebClient webClient = WebClient.builder()
                .defaultHeader("Accept", "text/plain, */*")
                .exchangeStrategies(strategies) // Explicitly set buffer limit
                .build();
        
        log.info("WebClient created with UNLIMITED buffer limit for logfile endpoint");

        log.info("=== STEP 1: Building WebClient and preparing request ===");
        log.info("Service: {} (ID: {})", service.getName(), service.getId());
        log.info("Actuator URL: {}", actuatorUrl);
        log.info("Request parameters: lines={}, level={}", lines, level);

        return webClient.get()
                .uri(actuatorUrl)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(60)) // Increased timeout for large logfiles
                .doOnSubscribe(subscription -> {
                    log.info("=== STEP 2: WebClient request initiated ===");
                    log.info("Requesting: GET {}", actuatorUrl);
                })
                .doOnNext(content -> {
                    log.info("=== STEP 3: Response received from actuator ===");
                    log.info("Response length: {} characters", content != null ? content.length() : 0);
                    if (content != null && content.length() > 0) {
                        log.info("First 300 chars: {}", 
                                content.substring(0, Math.min(300, content.length())));
                        log.info("Last 300 chars: {}", 
                                content.substring(Math.max(0, content.length() - 300)));
                    } else {
                        log.warn("WARNING: Response content is null or empty!");
                    }
                })
                .doOnError(error -> {
                    log.error("=== ERROR in WebClient request ===");
                    log.error("Service: {} (ID: {})", service.getName(), service.getId());
                    log.error("URL: {}", actuatorUrl);
                    log.error("Error type: {}", error.getClass().getName());
                    log.error("Error message: {}", error.getMessage());
                    if (error.getCause() != null) {
                        log.error("Caused by: {} - {}", error.getCause().getClass().getName(), error.getCause().getMessage());
                    }
                    log.error("Full stack trace:", error);
                })
                .map(logContent -> {
                    log.info("=== STEP 4: Parsing log content ===");
                    if (logContent == null) {
                        log.error("ERROR: Null log content received from service {}", service.getName());
                        log.error("This usually means the actuator /logfile endpoint returned null");
                        return new ArrayList<RemoteLogEntry>();
                    }
                    
                    if (logContent.trim().isEmpty()) {
                        log.error("ERROR: Empty log content received from service {}", service.getName());
                        log.error("The actuator /logfile endpoint returned an empty string");
                        log.error("Please check:");
                        log.error("  1. Is the logfile endpoint enabled? (management.endpoints.web.exposure.include=logfile)");
                        log.error("  2. Does the service have any logs?");
                        log.error("  3. Is the logfile path configured correctly?");
                        return new ArrayList<RemoteLogEntry>();
                    }
                    
                    log.info("Starting parse: {} characters, {} lines requested", 
                            logContent.length(), lines);
                    log.info("First 500 chars of log content: {}", 
                            logContent.substring(0, Math.min(500, logContent.length())));
                    
                    List<RemoteLogEntry> entries = parseLogContent(service, logContent, lines, level);
                    log.info("=== STEP 5: Parse complete ===");
                    log.info("Parsed {} log entries from service {}", entries.size(), service.getName());
                    
                    if (entries.isEmpty()) {
                        log.warn("WARNING: No log entries parsed from {} characters of content", logContent.length());
                        log.warn("This could mean:");
                        log.warn("  1. The log format doesn't match expected patterns");
                        log.warn("  2. All log lines were filtered out by level filter");
                        log.warn("  3. The log content format is unexpected");
                        log.warn("Sample log lines (first 3):");
                        String[] sampleLines = logContent.split("\n");
                        for (int i = 0; i < Math.min(3, sampleLines.length); i++) {
                            log.warn("  Line {}: {}", i + 1, sampleLines[i].substring(0, Math.min(200, sampleLines[i].length())));
                        }
                    } else {
                        RemoteLogEntry first = entries.get(0);
                        log.info("Sample entry: timestamp={}, level={}, logger={}, message={}", 
                                first.getTimestamp(), first.getLevel(), first.getLogger(),
                                first.getMessage() != null ? 
                                        first.getMessage().substring(0, Math.min(100, first.getMessage().length())) 
                                        : "null");
                    }
                    
                    return entries;
                })
                .onErrorResume(e -> {
                    log.error("=== ERROR: Failed to collect logs (onErrorResume) ===");
                    log.error("Service: {} (ID: {})", service.getName(), service.getId());
                    log.error("URL: {}", actuatorUrl);
                    log.error("Error type: {}", e.getClass().getName());
                    log.error("Error message: {}", e.getMessage());
                    if (e.getCause() != null) {
                        log.error("Caused by: {} - {}", e.getCause().getClass().getName(), e.getCause().getMessage());
                    }
                    log.error("Full stack trace:", e);
                    // Return empty list instead of failing completely
                    return Mono.just(new ArrayList<>());
                });
    }

    /**
     * Collect logs from a specific service instance.
     */
    public Mono<List<RemoteLogEntry>> collectLogsFromInstance(Long serviceId, String instanceId, Integer lines, String level) {
        ManagedService service = getService(serviceId);
        
        // For now, we collect from the service and filter by instance if needed
        // In a real implementation, you might have instance-specific endpoints
        return collectLogsFromService(serviceId, lines, level)
                .map(logs -> filterLogsByInstance(logs, instanceId));
    }

    /**
     * Search logs across a service with time filtering.
     */
    public Mono<List<RemoteLogEntry>> searchLogs(Long serviceId, String query, 
                                                  LocalDateTime startTime, LocalDateTime endTime, 
                                                  String level, Integer maxResults) {
        return collectLogsFromService(serviceId, 1000, level)
                .map(logs -> {
                    List<RemoteLogEntry> filtered = logs.stream()
                            .filter(log -> {
                                // Time filter
                                if (startTime != null && log.getTimestamp().isBefore(startTime)) {
                                    return false;
                                }
                                if (endTime != null && log.getTimestamp().isAfter(endTime)) {
                                    return false;
                                }
                                // Query filter
                                if (query != null && !query.trim().isEmpty()) {
                                    String lowerQuery = query.toLowerCase();
                                    return log.getMessage().toLowerCase().contains(lowerQuery) ||
                                           log.getLevel().toLowerCase().contains(lowerQuery) ||
                                           (log.getLogger() != null && log.getLogger().toLowerCase().contains(lowerQuery));
                                }
                                return true;
                            })
                            .limit(maxResults != null ? maxResults : 100)
                            .collect(Collectors.toList());
                    return filtered;
                });
    }

    /**
     * Get log statistics for a service.
     */
    public Mono<LogStatistics> getLogStatistics(Long serviceId, LocalDateTime startTime, LocalDateTime endTime) {
        return collectLogsFromService(serviceId, 10000, "ALL")
                .map(logs -> {
                    LogStatistics stats = new LogStatistics();
                    stats.setTotalLogs(logs.size());
                    
                    Map<String, Long> levelCounts = logs.stream()
                            .collect(Collectors.groupingBy(RemoteLogEntry::getLevel, Collectors.counting()));
                    stats.setLevelCounts(levelCounts);
                    
                    // Count errors
                    long errorCount = logs.stream()
                            .filter(log -> log.getLevel().equals("ERROR") || log.getLevel().equals("WARN"))
                            .count();
                    stats.setErrorCount(errorCount);
                    
                    // Time range
                    if (!logs.isEmpty()) {
                        stats.setFirstLogTime(logs.get(logs.size() - 1).getTimestamp());
                        stats.setLastLogTime(logs.get(0).getTimestamp());
                    }
                    
                    return stats;
                });
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

    private List<RemoteLogEntry> parseLogContent(ManagedService service, String logContent, 
                                                  Integer lines, String level) {
        List<RemoteLogEntry> entries = new ArrayList<>();
        
        if (logContent == null || logContent.trim().isEmpty()) {
            log.warn("Empty log content for service {}", service.getName());
            return entries;
        }

        String[] logLines = logContent.split("\n");
        int lineCount = 0;
        int maxLines = lines != null ? lines : 100;
        
        log.info("Parsing {} log lines from service {} (requested: {})", 
                logLines.length, service.getName(), maxLines);
        
        int parsedCount = 0;
        int skippedCount = 0;
        int filteredCount = 0;
        
        // Process lines in reverse to get most recent logs first
        for (int i = logLines.length - 1; i >= 0 && lineCount < maxLines; i--) {
            String line = logLines[i].trim();
            if (line.isEmpty()) {
                skippedCount++;
                continue;
            }

            RemoteLogEntry entry = parseLogLine(service, line);
            if (entry != null) {
                parsedCount++;
                // Filter by level if specified (only filter if level is not "ALL" or null)
                // When level is "ALL", include all log levels
                boolean shouldInclude = true;
                if (level != null && !level.trim().isEmpty() && !level.equalsIgnoreCase("ALL")) {
                    // Only filter if a specific level is requested (not "ALL")
                    shouldInclude = entry.getLevel().equalsIgnoreCase(level);
                }
                
                if (shouldInclude) {
                    entries.add(entry);
                    lineCount++;
                } else {
                    filteredCount++;
                }
            } else {
                skippedCount++;
            }
        }

        // Reverse to get chronological order (oldest first)
        Collections.reverse(entries);
        
        // Log level distribution for debugging
        Map<String, Long> levelDistribution = entries.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        RemoteLogEntry::getLevel, 
                        java.util.stream.Collectors.counting()));
        
        log.info("Parse summary for service {}: {} entries parsed, {} added, {} filtered by level, {} skipped", 
                service.getName(), parsedCount, entries.size(), filteredCount, skippedCount);
        log.info("Log level distribution: {}", levelDistribution);
        
        if (entries.isEmpty() && parsedCount > 0) {
            log.warn("WARNING: {} entries were parsed but all were filtered out by level filter '{}'", 
                    parsedCount, level);
            log.warn("Consider using level='ALL' to see all log levels");
        }
        
        return entries;
    }

    private RemoteLogEntry parseLogLine(ManagedService service, String line) {
        try {
            RemoteLogEntry entry = new RemoteLogEntry();
            entry.setServiceId(service.getId());
            entry.setServiceName(service.getName());
            entry.setInstanceId(service.getInstanceId());
            
            // Try Spring Boot default format: 2026-01-04 14:05:04 [thread] LEVEL logger - message
            Matcher springBootMatcher = SPRING_BOOT_LOG_PATTERN.matcher(line);
            if (springBootMatcher.matches()) {
                String timestampStr = springBootMatcher.group(1); // "2026-01-04 14:05:04"
                String thread = springBootMatcher.group(2);      // "http-nio-8500-exec-2"
                String level = springBootMatcher.group(3);       // "DEBUG"
                String logger = springBootMatcher.group(4);      // "o.s.web.servlet.DispatcherServlet"
                String message = springBootMatcher.group(5);     // "GET \"/actuator/health\", parameters={}"
                
                entry.setTimestamp(parseTimestamp(timestampStr));
                entry.setLevel(level.toUpperCase());
                entry.setThread(thread);
                entry.setLogger(logger);
                entry.setMessage(message);
                return entry;
            }
            
            // Try bracket format: [2024-01-01 12:00:00.000] LEVEL logger - message
            Matcher bracketMatcher = BRACKET_LOG_PATTERN.matcher(line);
            if (bracketMatcher.matches()) {
                String timestampStr = bracketMatcher.group(1); // "2024-01-01 12:00:00.000"
                String level = bracketMatcher.group(2);       // "LEVEL"
                String logger = bracketMatcher.group(3);      // "logger"
                String message = bracketMatcher.group(4);      // "message"
                
                entry.setTimestamp(parseTimestamp(timestampStr));
                entry.setLevel(level.toUpperCase());
                entry.setLogger(logger);
                entry.setMessage(message);
                return entry;
            }
            
            // Fallback: try to extract level and basic info
            String upperLine = line.toUpperCase();
            String detectedLevel = "INFO";
            if (upperLine.contains(" ERROR ") || upperLine.startsWith("ERROR")) {
                detectedLevel = "ERROR";
            } else if (upperLine.contains(" WARN ") || upperLine.startsWith("WARN")) {
                detectedLevel = "WARN";
            } else if (upperLine.contains(" DEBUG ") || upperLine.startsWith("DEBUG")) {
                detectedLevel = "DEBUG";
            } else if (upperLine.contains(" TRACE ") || upperLine.startsWith("TRACE")) {
                detectedLevel = "TRACE";
            }
            
            // Try to extract timestamp from beginning of line
            Pattern timestampPattern = Pattern.compile("^(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2})");
            Matcher tsMatcher = timestampPattern.matcher(line);
            if (tsMatcher.find()) {
                entry.setTimestamp(parseTimestamp(tsMatcher.group(1)));
            } else {
                entry.setTimestamp(LocalDateTime.now());
            }
            
            entry.setLevel(detectedLevel);
            entry.setMessage(line);
            return entry;
        } catch (Exception e) {
            log.debug("Failed to parse log line: {}", line, e);
            return null;
        }
    }

    private LocalDateTime parseTimestamp(String timestampStr) {
        try {
            // Try with milliseconds first: "2026-01-04 14:05:04.123"
            if (timestampStr.length() > 19 && timestampStr.charAt(19) == '.') {
                DateTimeFormatter withMillis = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
                return LocalDateTime.parse(timestampStr, withMillis);
            }
            // Try without milliseconds: "2026-01-04 14:05:04"
            return LocalDateTime.parse(timestampStr, LOG_DATE_FORMAT);
        } catch (Exception e) {
            log.debug("Failed to parse timestamp: {}, using current time", timestampStr);
            return LocalDateTime.now();
        }
    }

    private List<RemoteLogEntry> filterLogsByInstance(List<RemoteLogEntry> logs, String instanceId) {
        if (instanceId == null || instanceId.isEmpty()) {
            return logs;
        }
        return logs.stream()
                .filter(log -> instanceId.equals(log.getInstanceId()))
                .collect(Collectors.toList());
    }

    // DTOs

    @Data
    public static class RemoteLogEntry {
        private Long serviceId;
        private String serviceName;
        private String instanceId;
        @com.fasterxml.jackson.annotation.JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        private LocalDateTime timestamp;
        private String level;
        private String logger;
        private String message;
        private String thread;
    }

    @Data
    public static class LogStatistics {
        private long totalLogs;
        private long errorCount;
        private Map<String, Long> levelCounts;
        private LocalDateTime firstLogTime;
        private LocalDateTime lastLogTime;
    }
}



