package com.management.console.controller;

import com.management.console.service.ServiceLogManager;
import com.management.console.service.ServiceLogManager.*;
import com.management.console.service.ServiceRegistryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/logs")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class LogController {

    private final ServiceLogManager logManager;
    private final ServiceRegistryService serviceRegistryService;

    /**
     * Get unified logs (all services combined)
     */
    @GetMapping("/unified")
    public ResponseEntity<LogResponse> getUnifiedLogs(
            @RequestParam(defaultValue = "100") int lines,
            @RequestParam(defaultValue = "INFO") String level,
            @RequestParam(required = false) String serviceFilter) {
        
        log.debug("Fetching unified logs: lines={}, level={}, filter={}", lines, level, serviceFilter);
        
        LogLevel minLevel = LogLevel.fromString(level);
        List<LogEntry> entries = logManager.getUnifiedLogs(lines, minLevel, serviceFilter);
        
        return ResponseEntity.ok(LogResponse.builder()
                .entries(entries)
                .totalCount(entries.size())
                .category("unified")
                .build());
    }

    /**
     * Get logs for a specific service
     */
    @GetMapping("/service/{serviceId}")
    public ResponseEntity<LogResponse> getServiceLogs(
            @PathVariable Long serviceId,
            @RequestParam(defaultValue = "100") int lines,
            @RequestParam(defaultValue = "INFO") String level) {
        
        var service = serviceRegistryService.getService(serviceId);
        log.debug("Fetching logs for service: {}", service.getName());
        
        LogLevel minLevel = LogLevel.fromString(level);
        List<LogEntry> entries = logManager.getServiceLogs(serviceId, service.getName(), lines, minLevel);
        
        return ResponseEntity.ok(LogResponse.builder()
                .entries(entries)
                .totalCount(entries.size())
                .category("service")
                .serviceName(service.getName())
                .build());
    }

    /**
     * Get logs by category
     */
    @GetMapping("/category/{category}")
    public ResponseEntity<LogResponse> getLogsByCategory(
            @PathVariable String category,
            @RequestParam(defaultValue = "100") int lines) {
        
        log.debug("Fetching logs for category: {}", category);
        
        List<LogEntry> entries = logManager.getLogsByCategory(category, lines);
        
        return ResponseEntity.ok(LogResponse.builder()
                .entries(entries)
                .totalCount(entries.size())
                .category(category)
                .build());
    }

    /**
     * Get lifecycle logs
     */
    @GetMapping("/lifecycle")
    public ResponseEntity<LogResponse> getLifecycleLogs(
            @RequestParam(defaultValue = "100") int lines) {
        return getLogsByCategory("lifecycle", lines);
    }

    /**
     * Get health check logs
     */
    @GetMapping("/health")
    public ResponseEntity<LogResponse> getHealthLogs(
            @RequestParam(defaultValue = "100") int lines) {
        return getLogsByCategory("health", lines);
    }

    /**
     * Get metrics collection logs
     */
    @GetMapping("/metrics")
    public ResponseEntity<LogResponse> getMetricsLogs(
            @RequestParam(defaultValue = "100") int lines) {
        return getLogsByCategory("metrics", lines);
    }

    /**
     * Get AI analysis logs
     */
    @GetMapping("/ai")
    public ResponseEntity<LogResponse> getAILogs(
            @RequestParam(defaultValue = "100") int lines) {
        return getLogsByCategory("ai", lines);
    }

    /**
     * Get incident logs
     */
    @GetMapping("/incidents")
    public ResponseEntity<LogResponse> getIncidentLogs(
            @RequestParam(defaultValue = "100") int lines) {
        return getLogsByCategory("incidents", lines);
    }

    /**
     * Get audit trail logs
     */
    @GetMapping("/audit")
    public ResponseEntity<LogResponse> getAuditLogs(
            @RequestParam(defaultValue = "100") int lines) {
        return getLogsByCategory("audit", lines);
    }

    /**
     * Get error logs (all errors across system)
     */
    @GetMapping("/errors")
    public ResponseEntity<LogResponse> getErrorLogs(
            @RequestParam(defaultValue = "100") int lines) {
        return getLogsByCategory("errors", lines);
    }

    /**
     * Get console (application) logs
     */
    @GetMapping("/console")
    public ResponseEntity<LogResponse> getConsoleLogs(
            @RequestParam(defaultValue = "100") int lines) {
        return getLogsByCategory("console", lines);
    }

    /**
     * Search logs across all categories
     */
    @GetMapping("/search")
    public ResponseEntity<LogResponse> searchLogs(
            @RequestParam String query,
            @RequestParam(defaultValue = "100") int maxResults) {
        
        log.debug("Searching logs for: {}", query);
        
        List<LogEntry> entries = logManager.searchLogs(query, maxResults);
        
        return ResponseEntity.ok(LogResponse.builder()
                .entries(entries)
                .totalCount(entries.size())
                .category("search")
                .searchQuery(query)
                .build());
    }

    /**
     * Get all available log categories
     */
    @GetMapping("/categories")
    public ResponseEntity<List<LogCategory>> getLogCategories() {
        return ResponseEntity.ok(logManager.getLogCategories());
    }

    /**
     * Get log file paths for a service
     */
    @GetMapping("/service/{serviceId}/paths")
    public ResponseEntity<Map<String, String>> getServiceLogPaths(@PathVariable Long serviceId) {
        var service = serviceRegistryService.getService(serviceId);
        return ResponseEntity.ok(logManager.getServiceLogPaths(service.getName()));
    }

    /**
     * Get log directory information
     */
    @GetMapping("/info")
    public ResponseEntity<LogDirectoryInfo> getLogDirectoryInfo() {
        return ResponseEntity.ok(logManager.getLogDirectoryInfo());
    }

    /**
     * Clear logs for a specific service (Admin only)
     */
    @DeleteMapping("/service/{serviceId}")
    public ResponseEntity<Void> clearServiceLogs(@PathVariable Long serviceId) {
        var service = serviceRegistryService.getService(serviceId);
        log.info("Clearing logs for service: {}", service.getName());
        logManager.clearServiceLogs(service.getName());
        return ResponseEntity.noContent().build();
    }

    // Response DTO
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class LogResponse {
        private List<LogEntry> entries;
        private int totalCount;
        private String category;
        private String serviceName;
        private String searchQuery;
    }
}

