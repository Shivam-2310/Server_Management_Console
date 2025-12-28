package com.management.console.controller;

import com.management.console.service.*;
import com.management.console.service.JvmDiagnosticsService.*;
import com.management.console.service.LoggerManagementService.*;
import com.management.console.service.EnvironmentService.*;
import com.management.console.service.HttpTraceService.*;
import com.management.console.service.NotificationService.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * REST controller for JVM diagnostics, logging management, environment,
 * HTTP tracing, and notifications.
 */
@RestController
@RequestMapping("/api/diagnostics")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class DiagnosticsController {

    private final JvmDiagnosticsService jvmDiagnosticsService;
    private final LoggerManagementService loggerManagementService;
    private final EnvironmentService environmentService;
    private final HttpTraceService httpTraceService;
    private final NotificationService notificationService;

    // ==================== JVM Diagnostics ====================

    /**
     * Get local JVM info
     */
    @GetMapping("/jvm/info")
    public ResponseEntity<JvmInfo> getJvmInfo() {
        return ResponseEntity.ok(jvmDiagnosticsService.getLocalJvmInfo());
    }

    /**
     * Get local thread dump
     */
    @GetMapping("/jvm/threads")
    public ResponseEntity<ThreadDumpInfo> getThreadDump() {
        return ResponseEntity.ok(jvmDiagnosticsService.getLocalThreadDump());
    }

    /**
     * Get thread dump for remote service
     */
    @GetMapping("/jvm/threads/{serviceId}")
    public Mono<ResponseEntity<ThreadDumpInfo>> getRemoteThreadDump(@PathVariable Long serviceId) {
        return jvmDiagnosticsService.getThreadDump(serviceId)
                .map(ResponseEntity::ok);
    }

    /**
     * Download thread dump as text file
     */
    @GetMapping("/jvm/threads/download")
    public ResponseEntity<String> downloadThreadDump(@RequestParam(required = false) Long serviceId) {
        String dump = jvmDiagnosticsService.generateThreadDumpText(serviceId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=thread-dump.txt")
                .contentType(MediaType.TEXT_PLAIN)
                .body(dump);
    }

    /**
     * Get local heap info
     */
    @GetMapping("/jvm/heap")
    public ResponseEntity<HeapInfo> getHeapInfo() {
        return ResponseEntity.ok(jvmDiagnosticsService.getLocalHeapInfo());
    }

    /**
     * Get heap info for remote service
     */
    @GetMapping("/jvm/heap/{serviceId}")
    public Mono<ResponseEntity<HeapInfo>> getRemoteHeapInfo(@PathVariable Long serviceId) {
        return jvmDiagnosticsService.getHeapInfo(serviceId)
                .map(ResponseEntity::ok);
    }

    /**
     * Request garbage collection
     */
    @PostMapping("/jvm/gc")
    public ResponseEntity<GCRequestResult> requestGC(@RequestParam(required = false) Long serviceId) {
        return ResponseEntity.ok(jvmDiagnosticsService.requestGC(serviceId));
    }

    // ==================== Logger Management ====================

    /**
     * Get all local loggers
     */
    @GetMapping("/loggers")
    public ResponseEntity<List<LoggerInfo>> getLoggers() {
        return ResponseEntity.ok(loggerManagementService.getLocalLoggers());
    }

    /**
     * Get loggers from remote service
     */
    @GetMapping("/loggers/service/{serviceId}")
    public Mono<ResponseEntity<List<LoggerInfo>>> getRemoteLoggers(@PathVariable Long serviceId) {
        return loggerManagementService.getRemoteLoggers(serviceId)
                .map(ResponseEntity::ok);
    }

    /**
     * Search loggers by pattern
     */
    @GetMapping("/loggers/search")
    public ResponseEntity<List<LoggerInfo>> searchLoggers(@RequestParam String pattern) {
        return ResponseEntity.ok(loggerManagementService.searchLoggers(pattern));
    }

    /**
     * Set logger level locally
     */
    @PostMapping("/loggers/{loggerName}/level")
    public ResponseEntity<LoggerChangeResult> setLoggerLevel(
            @PathVariable String loggerName,
            @RequestParam String level) {
        return ResponseEntity.ok(loggerManagementService.setLocalLoggerLevel(loggerName, level));
    }

    /**
     * Set logger level on remote service
     */
    @PostMapping("/loggers/service/{serviceId}/{loggerName}/level")
    public Mono<ResponseEntity<LoggerChangeResult>> setRemoteLoggerLevel(
            @PathVariable Long serviceId,
            @PathVariable String loggerName,
            @RequestParam String level) {
        return loggerManagementService.setRemoteLoggerLevel(serviceId, loggerName, level)
                .map(ResponseEntity::ok);
    }

    /**
     * Reset logger to inherited level
     */
    @DeleteMapping("/loggers/{loggerName}/level")
    public ResponseEntity<LoggerChangeResult> resetLoggerLevel(@PathVariable String loggerName) {
        return ResponseEntity.ok(loggerManagementService.resetLoggerLevel(loggerName));
    }

    /**
     * Bulk update logger levels
     */
    @PostMapping("/loggers/bulk")
    public ResponseEntity<List<LoggerChangeResult>> bulkUpdateLoggers(@RequestBody Map<String, String> loggerLevels) {
        return ResponseEntity.ok(loggerManagementService.bulkUpdateLevels(loggerLevels));
    }

    /**
     * Get available log levels
     */
    @GetMapping("/loggers/levels")
    public ResponseEntity<List<String>> getAvailableLevels() {
        return ResponseEntity.ok(loggerManagementService.getAvailableLevels());
    }

    /**
     * Get logger change history
     */
    @GetMapping("/loggers/history")
    public ResponseEntity<List<LoggerChange>> getLoggerHistory(@RequestParam(defaultValue = "50") int limit) {
        return ResponseEntity.ok(loggerManagementService.getRecentChanges(limit));
    }

    /**
     * Get loggers grouped by package
     */
    @GetMapping("/loggers/grouped")
    public ResponseEntity<Map<String, List<LoggerInfo>>> getLoggersGrouped() {
        return ResponseEntity.ok(loggerManagementService.getLoggersGroupedByPackage());
    }

    // ==================== Environment ====================

    /**
     * Get local environment
     */
    @GetMapping("/env")
    public ResponseEntity<EnvironmentInfo> getEnvironment() {
        return ResponseEntity.ok(environmentService.getLocalEnvironment());
    }

    /**
     * Get environment from remote service
     */
    @GetMapping("/env/service/{serviceId}")
    public Mono<ResponseEntity<EnvironmentInfo>> getRemoteEnvironment(@PathVariable Long serviceId) {
        return environmentService.getRemoteEnvironment(serviceId)
                .map(ResponseEntity::ok);
    }

    /**
     * Get a specific property
     */
    @GetMapping("/env/property")
    public ResponseEntity<PropertyValue> getProperty(@RequestParam String name) {
        return ResponseEntity.ok(environmentService.getProperty(name));
    }

    /**
     * Search properties
     */
    @GetMapping("/env/search")
    public ResponseEntity<List<PropertyValue>> searchProperties(@RequestParam String pattern) {
        return ResponseEntity.ok(environmentService.searchProperties(pattern));
    }

    /**
     * Get configuration info
     */
    @GetMapping("/env/config")
    public ResponseEntity<ConfigurationInfo> getConfigurationInfo() {
        return ResponseEntity.ok(environmentService.getConfigurationInfo());
    }

    /**
     * Get property sources summary
     */
    @GetMapping("/env/sources")
    public ResponseEntity<List<PropertySourceSummary>> getPropertySources() {
        return ResponseEntity.ok(environmentService.getPropertySourcesSummary());
    }

    // ==================== HTTP Tracing ====================

    /**
     * Get recent HTTP traces
     */
    @GetMapping("/http/traces")
    public ResponseEntity<List<HttpTrace>> getHttpTraces(@RequestParam(defaultValue = "100") int limit) {
        return ResponseEntity.ok(httpTraceService.getRecentTraces(limit));
    }

    /**
     * Get HTTP traces from remote service
     */
    @GetMapping("/http/traces/service/{serviceId}")
    public Mono<ResponseEntity<List<HttpTrace>>> getRemoteHttpTraces(
            @PathVariable Long serviceId,
            @RequestParam(defaultValue = "100") int limit) {
        return httpTraceService.getRemoteTraces(serviceId, limit)
                .map(ResponseEntity::ok);
    }

    /**
     * Get filtered HTTP traces
     */
    @PostMapping("/http/traces/filter")
    public ResponseEntity<List<HttpTrace>> getFilteredTraces(@RequestBody TraceFilter filter) {
        return ResponseEntity.ok(httpTraceService.getFilteredTraces(filter));
    }

    /**
     * Get endpoint statistics
     */
    @GetMapping("/http/stats")
    public ResponseEntity<List<EndpointStats>> getEndpointStats() {
        return ResponseEntity.ok(httpTraceService.getEndpointStats());
    }

    /**
     * Get slowest endpoints
     */
    @GetMapping("/http/stats/slowest")
    public ResponseEntity<List<EndpointStats>> getSlowestEndpoints(@RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(httpTraceService.getSlowestEndpoints(limit));
    }

    /**
     * Get performance summary
     */
    @GetMapping("/http/performance")
    public ResponseEntity<PerformanceSummary> getPerformanceSummary() {
        return ResponseEntity.ok(httpTraceService.getPerformanceSummary());
    }

    /**
     * Get error traces
     */
    @GetMapping("/http/errors")
    public ResponseEntity<List<HttpTrace>> getErrorTraces(@RequestParam(defaultValue = "50") int limit) {
        return ResponseEntity.ok(httpTraceService.getErrorTraces(limit));
    }

    /**
     * Get slow requests
     */
    @GetMapping("/http/slow")
    public ResponseEntity<List<HttpTrace>> getSlowRequests(
            @RequestParam(defaultValue = "1000") double thresholdMs,
            @RequestParam(defaultValue = "50") int limit) {
        return ResponseEntity.ok(httpTraceService.getSlowRequests(thresholdMs, limit));
    }

    /**
     * Clear HTTP traces
     */
    @DeleteMapping("/http/traces")
    public ResponseEntity<Void> clearTraces() {
        httpTraceService.clearTraces();
        return ResponseEntity.noContent().build();
    }

    // ==================== Notifications ====================

    /**
     * Get available notification channels
     */
    @GetMapping("/notifications/channels")
    public ResponseEntity<List<ChannelInfo>> getNotificationChannels() {
        return ResponseEntity.ok(notificationService.getAvailableChannels());
    }

    /**
     * Test notification channel
     */
    @PostMapping("/notifications/test/{channelName}")
    public ResponseEntity<NotificationTestResult> testNotificationChannel(@PathVariable String channelName) {
        return ResponseEntity.ok(notificationService.testChannel(channelName));
    }

    /**
     * Send custom notification
     */
    @PostMapping("/notifications/send")
    public ResponseEntity<Void> sendNotification(@RequestBody NotificationRequest request) {
        notificationService.sendCustomNotification(
                request.getTitle(),
                request.getMessage(),
                request.getSeverity() != null ? request.getSeverity() : NotificationSeverity.INFO,
                request.getCategory(),
                request.getTargetChannels()
        );
        return ResponseEntity.accepted().build();
    }

    /**
     * Get notification history
     */
    @GetMapping("/notifications/history")
    public ResponseEntity<List<NotificationRecord>> getNotificationHistory(
            @RequestParam(defaultValue = "100") int limit) {
        return ResponseEntity.ok(notificationService.getNotificationHistory(limit));
    }

    /**
     * Get notification statistics
     */
    @GetMapping("/notifications/stats")
    public ResponseEntity<NotificationStats> getNotificationStats() {
        return ResponseEntity.ok(notificationService.getStats());
    }

    // ==================== DTOs ====================

    @lombok.Data
    public static class NotificationRequest {
        private String title;
        private String message;
        private NotificationSeverity severity;
        private String category;
        private List<String> targetChannels;
    }
}

