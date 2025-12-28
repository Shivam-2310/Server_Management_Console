package com.management.console.service;

import com.management.console.domain.entity.ManagedService;
import com.management.console.domain.enums.HealthStatus;
import com.management.console.repository.ManagedServiceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for managing and visualizing service dependencies.
 * Generates dependency graphs for service topology visualization.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ServiceDependencyService {

    private final ManagedServiceRepository serviceRepository;

    // Store dependencies (could be persisted in DB in production)
    private final Map<Long, Set<Long>> dependencies = new LinkedHashMap<>();

    /**
     * Add a dependency between services
     */
    public void addDependency(Long serviceId, Long dependsOnServiceId) {
        dependencies.computeIfAbsent(serviceId, k -> new HashSet<>()).add(dependsOnServiceId);
        log.info("Added dependency: {} -> {}", serviceId, dependsOnServiceId);
    }

    /**
     * Remove a dependency
     */
    public void removeDependency(Long serviceId, Long dependsOnServiceId) {
        Set<Long> deps = dependencies.get(serviceId);
        if (deps != null) {
            deps.remove(dependsOnServiceId);
        }
    }

    /**
     * Get all dependencies for a service
     */
    public Set<Long> getDependencies(Long serviceId) {
        return dependencies.getOrDefault(serviceId, Collections.emptySet());
    }

    /**
     * Get services that depend on a given service
     */
    public Set<Long> getDependents(Long serviceId) {
        return dependencies.entrySet().stream()
                .filter(e -> e.getValue().contains(serviceId))
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    /**
     * Generate full dependency graph for visualization
     */
    public DependencyGraph generateDependencyGraph() {
        List<ManagedService> services = serviceRepository.findAll();
        
        DependencyGraph graph = new DependencyGraph();
        graph.setTimestamp(System.currentTimeMillis());
        
        // Create nodes
        List<GraphNode> nodes = services.stream()
                .map(this::createNode)
                .collect(Collectors.toList());
        graph.setNodes(nodes);
        
        // Create edges from dependencies
        List<GraphEdge> edges = new ArrayList<>();
        for (Map.Entry<Long, Set<Long>> entry : dependencies.entrySet()) {
            Long sourceId = entry.getKey();
            for (Long targetId : entry.getValue()) {
                GraphEdge edge = new GraphEdge();
                edge.setSource(String.valueOf(sourceId));
                edge.setTarget(String.valueOf(targetId));
                edge.setType("dependency");
                edges.add(edge);
            }
        }
        graph.setEdges(edges);
        
        // Calculate statistics
        graph.setStats(calculateGraphStats(nodes, edges));
        
        return graph;
    }

    /**
     * Generate dependency tree for a specific service
     */
    public DependencyTree getDependencyTree(Long serviceId) {
        ManagedService service = serviceRepository.findById(serviceId).orElse(null);
        if (service == null) {
            return null;
        }

        DependencyTree tree = new DependencyTree();
        tree.setServiceId(serviceId);
        tree.setServiceName(service.getName());
        tree.setStatus(service.getHealthStatus());
        
        // Get dependencies recursively
        tree.setDependencies(buildDependencyTree(serviceId, new HashSet<>()));
        
        // Get dependents
        tree.setDependents(getDependents(serviceId).stream()
                .map(id -> {
                    ManagedService s = serviceRepository.findById(id).orElse(null);
                    if (s != null) {
                        DependencyNode node = new DependencyNode();
                        node.setServiceId(s.getId());
                        node.setServiceName(s.getName());
                        node.setStatus(s.getHealthStatus());
                        return node;
                    }
                    return null;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList()));
        
        return tree;
    }

    /**
     * Check for circular dependencies
     */
    public List<List<Long>> detectCircularDependencies() {
        List<List<Long>> cycles = new ArrayList<>();
        Set<Long> visited = new HashSet<>();
        Set<Long> recursionStack = new HashSet<>();

        for (Long serviceId : dependencies.keySet()) {
            List<Long> path = new ArrayList<>();
            if (detectCycleDFS(serviceId, visited, recursionStack, path, cycles)) {
                // Cycle found
            }
            visited.clear();
            recursionStack.clear();
        }

        return cycles;
    }

    /**
     * Get impact analysis - what would be affected if a service goes down
     */
    public ImpactAnalysis analyzeImpact(Long serviceId) {
        ImpactAnalysis analysis = new ImpactAnalysis();
        analysis.setServiceId(serviceId);
        
        ManagedService service = serviceRepository.findById(serviceId).orElse(null);
        if (service != null) {
            analysis.setServiceName(service.getName());
        }
        
        // Find all services that would be affected (direct and transitive)
        Set<Long> affected = new HashSet<>();
        Queue<Long> toProcess = new LinkedList<>();
        toProcess.add(serviceId);
        
        while (!toProcess.isEmpty()) {
            Long currentId = toProcess.poll();
            Set<Long> dependents = getDependents(currentId);
            for (Long dependentId : dependents) {
                if (!affected.contains(dependentId)) {
                    affected.add(dependentId);
                    toProcess.add(dependentId);
                }
            }
        }
        
        // Create affected services list
        List<AffectedService> affectedServices = affected.stream()
                .map(id -> {
                    ManagedService s = serviceRepository.findById(id).orElse(null);
                    if (s != null) {
                        AffectedService as = new AffectedService();
                        as.setServiceId(s.getId());
                        as.setServiceName(s.getName());
                        as.setEnvironment(s.getEnvironment());
                        as.setCurrentStatus(s.getHealthStatus());
                        
                        // Calculate dependency depth
                        as.setDependencyDepth(calculateDependencyDepth(serviceId, id));
                        
                        return as;
                    }
                    return null;
                })
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingInt(AffectedService::getDependencyDepth))
                .collect(Collectors.toList());
        
        analysis.setAffectedServices(affectedServices);
        analysis.setTotalAffected(affectedServices.size());
        
        // Calculate risk level
        analysis.setRiskLevel(calculateRiskLevel(affectedServices));
        
        return analysis;
    }

    /**
     * Get service topology metrics
     */
    public TopologyMetrics getTopologyMetrics() {
        List<ManagedService> services = serviceRepository.findAll();
        
        TopologyMetrics metrics = new TopologyMetrics();
        metrics.setTotalServices(services.size());
        metrics.setTotalDependencies(dependencies.values().stream()
                .mapToInt(Set::size)
                .sum());
        
        // Find services with most dependencies
        List<ServiceDependencyCount> mostDependencies = dependencies.entrySet().stream()
                .map(e -> {
                    ManagedService s = serviceRepository.findById(e.getKey()).orElse(null);
                    ServiceDependencyCount sdc = new ServiceDependencyCount();
                    sdc.setServiceId(e.getKey());
                    sdc.setServiceName(s != null ? s.getName() : "Unknown");
                    sdc.setCount(e.getValue().size());
                    return sdc;
                })
                .sorted(Comparator.comparingInt(ServiceDependencyCount::getCount).reversed())
                .limit(10)
                .collect(Collectors.toList());
        metrics.setMostDependencies(mostDependencies);
        
        // Find most depended-on services
        Map<Long, Integer> dependentCounts = new HashMap<>();
        for (Set<Long> deps : dependencies.values()) {
            for (Long dep : deps) {
                dependentCounts.merge(dep, 1, Integer::sum);
            }
        }
        
        List<ServiceDependencyCount> mostDependedOn = dependentCounts.entrySet().stream()
                .map(e -> {
                    ManagedService s = serviceRepository.findById(e.getKey()).orElse(null);
                    ServiceDependencyCount sdc = new ServiceDependencyCount();
                    sdc.setServiceId(e.getKey());
                    sdc.setServiceName(s != null ? s.getName() : "Unknown");
                    sdc.setCount(e.getValue());
                    return sdc;
                })
                .sorted(Comparator.comparingInt(ServiceDependencyCount::getCount).reversed())
                .limit(10)
                .collect(Collectors.toList());
        metrics.setMostDependedOn(mostDependedOn);
        
        // Identify isolated services (no dependencies in or out)
        Set<Long> allServiceIds = services.stream()
                .map(ManagedService::getId)
                .collect(Collectors.toSet());
        Set<Long> connectedServices = new HashSet<>();
        connectedServices.addAll(dependencies.keySet());
        dependencies.values().forEach(connectedServices::addAll);
        
        allServiceIds.removeAll(connectedServices);
        metrics.setIsolatedServices(allServiceIds.size());
        
        return metrics;
    }

    // Helper methods

    private GraphNode createNode(ManagedService service) {
        GraphNode node = new GraphNode();
        node.setId(String.valueOf(service.getId()));
        node.setLabel(service.getName());
        node.setType(service.getServiceType().name());
        node.setStatus(service.getHealthStatus());
        node.setEnvironment(service.getEnvironment());
        node.setColor(getStatusColor(service.getHealthStatus()));
        return node;
    }

    private GraphStats calculateGraphStats(List<GraphNode> nodes, List<GraphEdge> edges) {
        GraphStats stats = new GraphStats();
        stats.setNodeCount(nodes.size());
        stats.setEdgeCount(edges.size());
        
        long healthyCount = nodes.stream()
                .filter(n -> n.getStatus() == HealthStatus.HEALTHY)
                .count();
        stats.setHealthyPercentage(nodes.isEmpty() ? 0 : (double) healthyCount / nodes.size() * 100);
        
        return stats;
    }

    private List<DependencyNode> buildDependencyTree(Long serviceId, Set<Long> visited) {
        if (visited.contains(serviceId)) {
            return Collections.emptyList();
        }
        visited.add(serviceId);
        
        Set<Long> deps = getDependencies(serviceId);
        return deps.stream()
                .map(depId -> {
                    ManagedService s = serviceRepository.findById(depId).orElse(null);
                    if (s != null) {
                        DependencyNode node = new DependencyNode();
                        node.setServiceId(s.getId());
                        node.setServiceName(s.getName());
                        node.setStatus(s.getHealthStatus());
                        node.setChildren(buildDependencyTree(depId, new HashSet<>(visited)));
                        return node;
                    }
                    return null;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private boolean detectCycleDFS(Long current, Set<Long> visited, Set<Long> stack, 
                                   List<Long> path, List<List<Long>> cycles) {
        visited.add(current);
        stack.add(current);
        path.add(current);

        for (Long neighbor : getDependencies(current)) {
            if (!visited.contains(neighbor)) {
                if (detectCycleDFS(neighbor, visited, stack, path, cycles)) {
                    return true;
                }
            } else if (stack.contains(neighbor)) {
                // Found cycle
                int cycleStart = path.indexOf(neighbor);
                List<Long> cycle = new ArrayList<>(path.subList(cycleStart, path.size()));
                cycle.add(neighbor);
                cycles.add(cycle);
            }
        }

        stack.remove(current);
        path.remove(path.size() - 1);
        return false;
    }

    private int calculateDependencyDepth(Long sourceId, Long targetId) {
        if (sourceId.equals(targetId)) return 0;
        
        Queue<Long> queue = new LinkedList<>();
        Map<Long, Integer> depths = new HashMap<>();
        queue.add(sourceId);
        depths.put(sourceId, 0);

        while (!queue.isEmpty()) {
            Long current = queue.poll();
            int currentDepth = depths.get(current);
            
            for (Long dependent : getDependents(current)) {
                if (dependent.equals(targetId)) {
                    return currentDepth + 1;
                }
                if (!depths.containsKey(dependent)) {
                    depths.put(dependent, currentDepth + 1);
                    queue.add(dependent);
                }
            }
        }
        
        return -1; // Not found
    }

    private String calculateRiskLevel(List<AffectedService> affected) {
        if (affected.isEmpty()) return "LOW";
        
        long criticalCount = affected.stream()
                .filter(s -> "PROD".equalsIgnoreCase(s.getEnvironment()))
                .count();
        
        if (criticalCount > 5) return "CRITICAL";
        if (criticalCount > 2 || affected.size() > 10) return "HIGH";
        if (affected.size() > 5) return "MEDIUM";
        return "LOW";
    }

    private String getStatusColor(HealthStatus status) {
        if (status == null) return "#808080";
        return switch (status) {
            case HEALTHY -> "#00FF00";
            case DEGRADED -> "#FFA500";
            case DOWN, CRITICAL -> "#FF0000";
            case UNKNOWN -> "#808080";
        };
    }

    // DTOs

    @lombok.Data
    public static class DependencyGraph {
        private long timestamp;
        private List<GraphNode> nodes;
        private List<GraphEdge> edges;
        private GraphStats stats;
    }

    @lombok.Data
    public static class GraphNode {
        private String id;
        private String label;
        private String type;
        private HealthStatus status;
        private String environment;
        private String color;
    }

    @lombok.Data
    public static class GraphEdge {
        private String source;
        private String target;
        private String type;
    }

    @lombok.Data
    public static class GraphStats {
        private int nodeCount;
        private int edgeCount;
        private double healthyPercentage;
    }

    @lombok.Data
    public static class DependencyTree {
        private Long serviceId;
        private String serviceName;
        private HealthStatus status;
        private List<DependencyNode> dependencies;
        private List<DependencyNode> dependents;
    }

    @lombok.Data
    public static class DependencyNode {
        private Long serviceId;
        private String serviceName;
        private HealthStatus status;
        private List<DependencyNode> children;
    }

    @lombok.Data
    public static class ImpactAnalysis {
        private Long serviceId;
        private String serviceName;
        private int totalAffected;
        private String riskLevel;
        private List<AffectedService> affectedServices;
    }

    @lombok.Data
    public static class AffectedService {
        private Long serviceId;
        private String serviceName;
        private String environment;
        private HealthStatus currentStatus;
        private int dependencyDepth;
    }

    @lombok.Data
    public static class TopologyMetrics {
        private int totalServices;
        private int totalDependencies;
        private int isolatedServices;
        private List<ServiceDependencyCount> mostDependencies;
        private List<ServiceDependencyCount> mostDependedOn;
    }

    @lombok.Data
    public static class ServiceDependencyCount {
        private Long serviceId;
        private String serviceName;
        private int count;
    }
}

