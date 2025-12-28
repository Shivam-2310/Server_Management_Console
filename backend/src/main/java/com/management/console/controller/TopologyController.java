package com.management.console.controller;

import com.management.console.service.ServiceDependencyService;
import com.management.console.service.ServiceDependencyService.*;
import com.management.console.service.StartupAnalyticsService;
import com.management.console.service.StartupAnalyticsService.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Set;

/**
 * REST controller for service topology (dependencies) and startup analytics.
 */
@RestController
@RequestMapping("/api/topology")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class TopologyController {

    private final ServiceDependencyService dependencyService;
    private final StartupAnalyticsService startupAnalyticsService;

    // ==================== Dependency Graph ====================

    /**
     * Get full dependency graph
     */
    @GetMapping("/graph")
    public ResponseEntity<DependencyGraph> getDependencyGraph() {
        return ResponseEntity.ok(dependencyService.generateDependencyGraph());
    }

    /**
     * Get dependency tree for a service
     */
    @GetMapping("/tree/{serviceId}")
    public ResponseEntity<DependencyTree> getDependencyTree(@PathVariable Long serviceId) {
        DependencyTree tree = dependencyService.getDependencyTree(serviceId);
        if (tree == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(tree);
    }

    /**
     * Get dependencies of a service
     */
    @GetMapping("/dependencies/{serviceId}")
    public ResponseEntity<Set<Long>> getDependencies(@PathVariable Long serviceId) {
        return ResponseEntity.ok(dependencyService.getDependencies(serviceId));
    }

    /**
     * Get services that depend on a service
     */
    @GetMapping("/dependents/{serviceId}")
    public ResponseEntity<Set<Long>> getDependents(@PathVariable Long serviceId) {
        return ResponseEntity.ok(dependencyService.getDependents(serviceId));
    }

    /**
     * Add a dependency
     */
    @PostMapping("/dependencies")
    public ResponseEntity<Void> addDependency(
            @RequestParam Long serviceId,
            @RequestParam Long dependsOnServiceId) {
        dependencyService.addDependency(serviceId, dependsOnServiceId);
        return ResponseEntity.ok().build();
    }

    /**
     * Remove a dependency
     */
    @DeleteMapping("/dependencies")
    public ResponseEntity<Void> removeDependency(
            @RequestParam Long serviceId,
            @RequestParam Long dependsOnServiceId) {
        dependencyService.removeDependency(serviceId, dependsOnServiceId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Analyze impact if a service goes down
     */
    @GetMapping("/impact/{serviceId}")
    public ResponseEntity<ImpactAnalysis> analyzeImpact(@PathVariable Long serviceId) {
        return ResponseEntity.ok(dependencyService.analyzeImpact(serviceId));
    }

    /**
     * Detect circular dependencies
     */
    @GetMapping("/circular")
    public ResponseEntity<List<List<Long>>> detectCircularDependencies() {
        return ResponseEntity.ok(dependencyService.detectCircularDependencies());
    }

    /**
     * Get topology metrics
     */
    @GetMapping("/metrics")
    public ResponseEntity<TopologyMetrics> getTopologyMetrics() {
        return ResponseEntity.ok(dependencyService.getTopologyMetrics());
    }

    // ==================== Startup Analytics ====================

    /**
     * Get local application startup info
     */
    @GetMapping("/startup/local")
    public ResponseEntity<StartupInfo> getLocalStartupInfo() {
        StartupInfo info = startupAnalyticsService.getLocalStartupInfo();
        if (info == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(info);
    }

    /**
     * Get startup info from remote service
     */
    @GetMapping("/startup/service/{serviceId}")
    public Mono<ResponseEntity<StartupInfo>> getRemoteStartupInfo(@PathVariable Long serviceId) {
        return startupAnalyticsService.getRemoteStartupInfo(serviceId)
                .map(ResponseEntity::ok);
    }

    /**
     * Get startup history for a service
     */
    @GetMapping("/startup/history/{serviceId}")
    public ResponseEntity<List<StartupRecord>> getStartupHistory(@PathVariable Long serviceId) {
        return ResponseEntity.ok(startupAnalyticsService.getStartupHistory(serviceId));
    }

    /**
     * Get startup statistics for a service
     */
    @GetMapping("/startup/stats/{serviceId}")
    public ResponseEntity<StartupStats> getStartupStats(@PathVariable Long serviceId) {
        return ResponseEntity.ok(startupAnalyticsService.getStartupStats(serviceId));
    }

    /**
     * Get startup timeline for a service
     */
    @GetMapping("/startup/timeline/{serviceId}")
    public Mono<ResponseEntity<StartupTimeline>> getStartupTimeline(@PathVariable Long serviceId) {
        return startupAnalyticsService.getStartupTimeline(serviceId)
                .map(ResponseEntity::ok);
    }

    /**
     * Get aggregated startup metrics
     */
    @GetMapping("/startup/aggregated")
    public ResponseEntity<AggregatedStartupMetrics> getAggregatedMetrics() {
        return ResponseEntity.ok(startupAnalyticsService.getAggregatedMetrics());
    }

    /**
     * Compare startup times between services
     */
    @PostMapping("/startup/compare")
    public ResponseEntity<StartupComparison> compareStartups(@RequestBody List<Long> serviceIds) {
        return ResponseEntity.ok(startupAnalyticsService.compareStartups(serviceIds));
    }

    /**
     * Record a service startup (called when a service starts)
     */
    @PostMapping("/startup/record")
    public ResponseEntity<Void> recordStartup(
            @RequestParam Long serviceId,
            @RequestParam long startTimeMs,
            @RequestParam long readyTimeMs,
            @RequestParam(defaultValue = "SUCCESS") String status) {
        startupAnalyticsService.recordServiceStartup(serviceId, startTimeMs, readyTimeMs, status);
        return ResponseEntity.ok().build();
    }
}

