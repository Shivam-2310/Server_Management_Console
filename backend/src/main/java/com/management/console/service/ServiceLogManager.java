package com.management.console.service;

import com.management.console.domain.entity.ManagedService;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Manages logs for all managed services.
 * Each service gets its own log directory with stdout and stderr logs.
 * Also provides unified log aggregation.
 */
@Service
@Slf4j
public class ServiceLogManager {

    @Value("${app.logging.base-dir:./logs}")
    private String baseLogDir;

    @Value("${app.logging.max-lines-per-read:1000}")
    private int maxLinesPerRead;

    @Value("${app.logging.max-file-size-mb:100}")
    private int maxFileSizeMb;

    private static final String SERVICES_DIR = "services";
    private static final String UNIFIED_DIR = "unified";
    private static final DateTimeFormatter LOG_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private static final DateTimeFormatter FILE_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    // Logger for unified service output
    private static final Logger SERVICE_OUTPUT_LOGGER = LoggerFactory.getLogger("SERVICE_OUTPUT");

    // Track active log writers for each service
    private final Map<Long, ServiceLogWriter> activeLogWriters = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        createDirectoryStructure();
        log.info("ServiceLogManager initialized with base directory: {}", baseLogDir);
    }

    private void createDirectoryStructure() {
        try {
            // Create main directories
            Files.createDirectories(Paths.get(baseLogDir, SERVICES_DIR));
            Files.createDirectories(Paths.get(baseLogDir, UNIFIED_DIR));
            Files.createDirectories(Paths.get(baseLogDir, "console"));
            Files.createDirectories(Paths.get(baseLogDir, "lifecycle"));
            Files.createDirectories(Paths.get(baseLogDir, "health"));
            Files.createDirectories(Paths.get(baseLogDir, "metrics"));
            Files.createDirectories(Paths.get(baseLogDir, "ai"));
            Files.createDirectories(Paths.get(baseLogDir, "incidents"));
            Files.createDirectories(Paths.get(baseLogDir, "audit"));
            Files.createDirectories(Paths.get(baseLogDir, "errors"));
            
            log.info("Log directory structure created at: {}", baseLogDir);
        } catch (IOException e) {
            log.error("Failed to create log directory structure: {}", e.getMessage());
        }
    }

    /**
     * Get or create a log writer for a service
     */
    public ServiceLogWriter getLogWriter(ManagedService service) {
        return activeLogWriters.computeIfAbsent(service.getId(), id -> {
            try {
                return new ServiceLogWriter(service, baseLogDir);
            } catch (IOException e) {
                log.error("Failed to create log writer for service {}: {}", service.getName(), e.getMessage());
                return null;
            }
        });
    }

    /**
     * Close log writer for a service
     */
    public void closeLogWriter(Long serviceId) {
        ServiceLogWriter writer = activeLogWriters.remove(serviceId);
        if (writer != null) {
            writer.close();
        }
    }

    /**
     * Log a message for a specific service
     */
    public void logServiceOutput(ManagedService service, String message, LogLevel level) {
        String timestamp = LocalDateTime.now().format(LOG_DATE_FORMAT);
        String formattedMessage = String.format("[%s] [%s] [%s] %s", 
                timestamp, service.getName(), level.name(), message);

        // Write to service-specific log
        ServiceLogWriter writer = getLogWriter(service);
        if (writer != null) {
            writer.write(formattedMessage, level);
        }

        // Write to unified log
        SERVICE_OUTPUT_LOGGER.info("[{}] [{}] {}", service.getName(), level.name(), message);
    }

    /**
     * Log stdout for a service
     */
    public void logStdout(ManagedService service, String message) {
        logServiceOutput(service, message, LogLevel.INFO);
    }

    /**
     * Log stderr for a service
     */
    public void logStderr(ManagedService service, String message) {
        logServiceOutput(service, message, LogLevel.ERROR);
    }

    /**
     * Log a lifecycle event
     */
    public void logLifecycleEvent(ManagedService service, String action, String details) {
        String message = String.format("LIFECYCLE | Service: %s | Action: %s | Details: %s",
                service.getName(), action, details);
        logServiceOutput(service, message, LogLevel.INFO);
    }

    /**
     * Get recent logs for a service
     */
    public List<LogEntry> getServiceLogs(Long serviceId, String serviceName, int lines, LogLevel minLevel) {
        Path logDir = Paths.get(baseLogDir, SERVICES_DIR, serviceName);
        List<LogEntry> entries = new ArrayList<>();

        try {
            // Read stdout log
            Path stdoutLog = logDir.resolve("stdout.log");
            if (Files.exists(stdoutLog)) {
                entries.addAll(readLogFile(stdoutLog, lines / 2, minLevel, serviceName));
            }

            // Read stderr log
            Path stderrLog = logDir.resolve("stderr.log");
            if (Files.exists(stderrLog)) {
                entries.addAll(readLogFile(stderrLog, lines / 2, minLevel, serviceName));
            }

            // Sort by timestamp
            entries.sort(Comparator.comparing(LogEntry::getTimestamp).reversed());

            // Limit to requested lines
            if (entries.size() > lines) {
                entries = entries.subList(0, lines);
            }

        } catch (Exception e) {
            log.error("Failed to read logs for service {}: {}", serviceName, e.getMessage());
        }

        return entries;
    }

    /**
     * Get unified logs (all services)
     */
    public List<LogEntry> getUnifiedLogs(int lines, LogLevel minLevel, String serviceFilter) {
        Path unifiedLog = Paths.get(baseLogDir, UNIFIED_DIR, "all-services.log");
        List<LogEntry> entries = new ArrayList<>();

        try {
            if (Files.exists(unifiedLog)) {
                entries = readLogFile(unifiedLog, lines, minLevel, null);
                
                // Apply service filter if provided
                if (serviceFilter != null && !serviceFilter.isEmpty()) {
                    entries = entries.stream()
                            .filter(e -> e.getServiceName() != null && 
                                    e.getServiceName().toLowerCase().contains(serviceFilter.toLowerCase()))
                            .collect(Collectors.toList());
                }
            }
        } catch (Exception e) {
            log.error("Failed to read unified logs: {}", e.getMessage());
        }

        return entries;
    }

    /**
     * Get logs by category
     */
    public List<LogEntry> getLogsByCategory(String category, int lines) {
        Path logFile = Paths.get(baseLogDir, category, getLogFileName(category));
        List<LogEntry> entries = new ArrayList<>();

        try {
            if (Files.exists(logFile)) {
                entries = readLogFile(logFile, lines, LogLevel.DEBUG, null);
            }
        } catch (Exception e) {
            log.error("Failed to read {} logs: {}", category, e.getMessage());
        }

        return entries;
    }

    private String getLogFileName(String category) {
        return switch (category) {
            case "lifecycle" -> "lifecycle-actions.log";
            case "health" -> "health-checks.log";
            case "metrics" -> "metrics-collection.log";
            case "ai" -> "ai-analysis.log";
            case "incidents" -> "incidents.log";
            case "audit" -> "audit-trail.log";
            case "errors" -> "errors.log";
            case "console" -> "management-console.log";
            default -> category + ".log";
        };
    }

    /**
     * Read log file with tail-like behavior
     */
    private List<LogEntry> readLogFile(Path logFile, int lines, LogLevel minLevel, String serviceName) throws IOException {
        List<LogEntry> entries = new ArrayList<>();
        
        if (!Files.exists(logFile)) {
            return entries;
        }

        // Read last N lines efficiently
        List<String> allLines = new ArrayList<>();
        try (Stream<String> stream = Files.lines(logFile, StandardCharsets.UTF_8)) {
            allLines = stream.collect(Collectors.toList());
        }

        int startIndex = Math.max(0, allLines.size() - Math.min(lines, maxLinesPerRead));
        
        for (int i = startIndex; i < allLines.size(); i++) {
            String line = allLines.get(i);
            LogEntry entry = parseLogLine(line, serviceName);
            if (entry != null && entry.getLevel().ordinal() >= minLevel.ordinal()) {
                entries.add(entry);
            }
        }

        return entries;
    }

    /**
     * Parse a log line into a LogEntry
     */
    private LogEntry parseLogLine(String line, String defaultServiceName) {
        if (line == null || line.trim().isEmpty()) {
            return null;
        }

        try {
            // Try to parse structured log format: [timestamp] [service] [level] message
            // or: timestamp | level | message
            LogEntry entry = new LogEntry();
            
            if (line.startsWith("[")) {
                // Format: [timestamp] [service] [level] message
                int firstClose = line.indexOf(']');
                if (firstClose > 0) {
                    String timestamp = line.substring(1, firstClose);
                    entry.setTimestamp(timestamp);
                    
                    String rest = line.substring(firstClose + 1).trim();
                    if (rest.startsWith("[")) {
                        int secondClose = rest.indexOf(']');
                        if (secondClose > 0) {
                            entry.setServiceName(rest.substring(1, secondClose));
                            rest = rest.substring(secondClose + 1).trim();
                            
                            if (rest.startsWith("[")) {
                                int thirdClose = rest.indexOf(']');
                                if (thirdClose > 0) {
                                    entry.setLevel(LogLevel.fromString(rest.substring(1, thirdClose)));
                                    entry.setMessage(rest.substring(thirdClose + 1).trim());
                                }
                            }
                        }
                    }
                }
            } else if (line.contains(" | ")) {
                // Format: timestamp | level | message
                String[] parts = line.split(" \\| ", 3);
                if (parts.length >= 2) {
                    entry.setTimestamp(parts[0].trim());
                    entry.setLevel(LogLevel.fromString(parts[1].trim()));
                    entry.setMessage(parts.length > 2 ? parts[2].trim() : "");
                    entry.setServiceName(defaultServiceName);
                }
            } else {
                // Simple format
                entry.setTimestamp(LocalDateTime.now().format(LOG_DATE_FORMAT));
                entry.setLevel(LogLevel.INFO);
                entry.setMessage(line);
                entry.setServiceName(defaultServiceName);
            }

            if (entry.getMessage() == null || entry.getMessage().isEmpty()) {
                entry.setMessage(line);
            }
            if (entry.getLevel() == null) {
                entry.setLevel(LogLevel.INFO);
            }
            if (entry.getTimestamp() == null) {
                entry.setTimestamp(LocalDateTime.now().format(LOG_DATE_FORMAT));
            }

            return entry;
        } catch (Exception e) {
            // If parsing fails, return simple entry
            LogEntry entry = new LogEntry();
            entry.setTimestamp(LocalDateTime.now().format(LOG_DATE_FORMAT));
            entry.setLevel(LogLevel.INFO);
            entry.setMessage(line);
            entry.setServiceName(defaultServiceName);
            return entry;
        }
    }

    /**
     * Get log file paths for a service
     */
    public Map<String, String> getServiceLogPaths(String serviceName) {
        Path serviceDir = Paths.get(baseLogDir, SERVICES_DIR, serviceName);
        Map<String, String> paths = new HashMap<>();
        paths.put("stdout", serviceDir.resolve("stdout.log").toString());
        paths.put("stderr", serviceDir.resolve("stderr.log").toString());
        paths.put("combined", serviceDir.resolve("combined.log").toString());
        return paths;
    }

    /**
     * Get all available log categories
     */
    public List<LogCategory> getLogCategories() {
        List<LogCategory> categories = new ArrayList<>();
        
        categories.add(new LogCategory("unified", "Unified Logs", "All service logs in one place", 
                Paths.get(baseLogDir, UNIFIED_DIR, "all-services.log").toString()));
        categories.add(new LogCategory("console", "Console Logs", "Management console application logs",
                Paths.get(baseLogDir, "console", "management-console.log").toString()));
        categories.add(new LogCategory("lifecycle", "Lifecycle Logs", "Service start/stop/restart events",
                Paths.get(baseLogDir, "lifecycle", "lifecycle-actions.log").toString()));
        categories.add(new LogCategory("health", "Health Check Logs", "Health monitoring results",
                Paths.get(baseLogDir, "health", "health-checks.log").toString()));
        categories.add(new LogCategory("metrics", "Metrics Logs", "Metrics collection events",
                Paths.get(baseLogDir, "metrics", "metrics-collection.log").toString()));
        categories.add(new LogCategory("ai", "AI Analysis Logs", "AI-driven analysis and anomaly detection",
                Paths.get(baseLogDir, "ai", "ai-analysis.log").toString()));
        categories.add(new LogCategory("incidents", "Incident Logs", "Incident creation and resolution",
                Paths.get(baseLogDir, "incidents", "incidents.log").toString()));
        categories.add(new LogCategory("audit", "Audit Logs", "Complete audit trail of all actions",
                Paths.get(baseLogDir, "audit", "audit-trail.log").toString()));
        categories.add(new LogCategory("errors", "Error Logs", "All errors across the system",
                Paths.get(baseLogDir, "errors", "errors.log").toString()));

        return categories;
    }

    /**
     * Search logs across all categories
     */
    public List<LogEntry> searchLogs(String query, int maxResults) {
        List<LogEntry> results = new ArrayList<>();
        String queryLower = query.toLowerCase();

        // Search in unified log
        List<LogEntry> unifiedLogs = getUnifiedLogs(maxResults * 2, LogLevel.DEBUG, null);
        for (LogEntry entry : unifiedLogs) {
            if (entry.getMessage().toLowerCase().contains(queryLower) ||
                (entry.getServiceName() != null && entry.getServiceName().toLowerCase().contains(queryLower))) {
                results.add(entry);
                if (results.size() >= maxResults) break;
            }
        }

        return results;
    }

    /**
     * Clear logs for a service
     */
    public void clearServiceLogs(String serviceName) {
        Path serviceDir = Paths.get(baseLogDir, SERVICES_DIR, serviceName);
        try {
            if (Files.exists(serviceDir)) {
                Files.walk(serviceDir)
                        .filter(Files::isRegularFile)
                        .forEach(path -> {
                            try {
                                Files.delete(path);
                            } catch (IOException e) {
                                log.warn("Could not delete log file: {}", path);
                            }
                        });
            }
            log.info("Cleared logs for service: {}", serviceName);
        } catch (IOException e) {
            log.error("Failed to clear logs for service {}: {}", serviceName, e.getMessage());
        }
    }

    /**
     * Get log directory info
     */
    public LogDirectoryInfo getLogDirectoryInfo() {
        LogDirectoryInfo info = new LogDirectoryInfo();
        info.setBaseDirectory(baseLogDir);
        
        try {
            long totalSize = Files.walk(Paths.get(baseLogDir))
                    .filter(Files::isRegularFile)
                    .mapToLong(path -> {
                        try {
                            return Files.size(path);
                        } catch (IOException e) {
                            return 0;
                        }
                    })
                    .sum();
            info.setTotalSizeBytes(totalSize);
            info.setTotalSizeMB(totalSize / (1024 * 1024));
            
            long fileCount = Files.walk(Paths.get(baseLogDir))
                    .filter(Files::isRegularFile)
                    .count();
            info.setFileCount(fileCount);
            
        } catch (IOException e) {
            log.error("Failed to get log directory info: {}", e.getMessage());
        }

        return info;
    }

    // Inner classes

    /**
     * Writes logs for a specific service
     */
    public static class ServiceLogWriter {
        private final Path serviceLogDir;
        private final String serviceName;
        private BufferedWriter stdoutWriter;
        private BufferedWriter stderrWriter;
        private BufferedWriter combinedWriter;

        public ServiceLogWriter(ManagedService service, String baseLogDir) throws IOException {
            this.serviceName = service.getName();
            this.serviceLogDir = Paths.get(baseLogDir, SERVICES_DIR, serviceName);
            Files.createDirectories(serviceLogDir);
            
            // Open writers in append mode
            stdoutWriter = Files.newBufferedWriter(
                    serviceLogDir.resolve("stdout.log"),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            stderrWriter = Files.newBufferedWriter(
                    serviceLogDir.resolve("stderr.log"),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            combinedWriter = Files.newBufferedWriter(
                    serviceLogDir.resolve("combined.log"),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        }

        public synchronized void write(String message, LogLevel level) {
            try {
                String line = message + System.lineSeparator();
                
                // Write to appropriate stream
                if (level == LogLevel.ERROR || level == LogLevel.WARN) {
                    stderrWriter.write(line);
                    stderrWriter.flush();
                } else {
                    stdoutWriter.write(line);
                    stdoutWriter.flush();
                }
                
                // Always write to combined
                combinedWriter.write(line);
                combinedWriter.flush();
                
            } catch (IOException e) {
                LoggerFactory.getLogger(ServiceLogWriter.class)
                        .error("Failed to write log for service {}: {}", serviceName, e.getMessage());
            }
        }

        public void close() {
            try {
                if (stdoutWriter != null) stdoutWriter.close();
                if (stderrWriter != null) stderrWriter.close();
                if (combinedWriter != null) combinedWriter.close();
            } catch (IOException e) {
                LoggerFactory.getLogger(ServiceLogWriter.class)
                        .error("Failed to close log writer for service {}: {}", serviceName, e.getMessage());
            }
        }
    }

    public enum LogLevel {
        DEBUG, INFO, WARN, ERROR, FATAL;

        public static LogLevel fromString(String level) {
            if (level == null) return INFO;
            try {
                return LogLevel.valueOf(level.toUpperCase().trim());
            } catch (Exception e) {
                if (level.toUpperCase().contains("ERR")) return ERROR;
                if (level.toUpperCase().contains("WARN")) return WARN;
                if (level.toUpperCase().contains("DEBUG")) return DEBUG;
                return INFO;
            }
        }
    }

    @lombok.Data
    public static class LogEntry {
        private String timestamp;
        private String serviceName;
        private LogLevel level;
        private String message;
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    public static class LogCategory {
        private String id;
        private String name;
        private String description;
        private String filePath;
    }

    @lombok.Data
    public static class LogDirectoryInfo {
        private String baseDirectory;
        private long totalSizeBytes;
        private long totalSizeMB;
        private long fileCount;
    }
}

