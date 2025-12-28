package com.management.console.service.ai;

import com.management.console.domain.entity.HealthCheckResult;
import com.management.console.domain.entity.Incident;
import com.management.console.domain.entity.ManagedService;
import com.management.console.domain.entity.ServiceMetrics;
import com.management.console.domain.enums.HealthStatus;
import com.management.console.domain.enums.IncidentSeverity;
import com.management.console.dto.AIAnalysisDTO;
import com.management.console.dto.AIAnalysisDTO.AIRecommendation;
import com.management.console.repository.HealthCheckResultRepository;
import com.management.console.repository.IncidentRepository;
import com.management.console.repository.ManagedServiceRepository;
import com.management.console.repository.ServiceMetricsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AIIntelligenceService {

    private final OllamaClient ollamaClient;
    private final ManagedServiceRepository serviceRepository;
    private final ServiceMetricsRepository metricsRepository;
    private final HealthCheckResultRepository healthCheckRepository;
    private final IncidentRepository incidentRepository;

    private static final String SYSTEM_PROMPT = """
        You are an expert DevOps AI assistant analyzing server and application health.
        Provide concise, actionable insights based on the metrics and health data provided.
        Focus on identifying issues, explaining root causes, and recommending specific actions.
        Keep responses brief and technical. Use bullet points where appropriate.
        Never recommend actions that could cause data loss without explicit user confirmation.
        """;

    @Transactional(readOnly = true)
    public AIAnalysisDTO analyzeService(Long serviceId) {
        log.info("Performing AI analysis for service: {}", serviceId);

        ManagedService service = serviceRepository.findById(serviceId)
                .orElseThrow(() -> new RuntimeException("Service not found: " + serviceId));

        // Gather data
        LocalDateTime since = LocalDateTime.now().minusHours(1);
        List<ServiceMetrics> recentMetrics = metricsRepository.findRecentMetrics(serviceId, since);
        List<HealthCheckResult> recentHealthChecks = healthCheckRepository.findRecentChecks(serviceId, since);
        List<Incident> activeIncidents = incidentRepository.findActiveIncidentsByService(serviceId);

        // Build analysis context
        String analysisContext = buildAnalysisContext(service, recentMetrics, recentHealthChecks, activeIncidents);

        // Get AI analysis
        String aiResponse = ollamaClient.generateChatResponse(SYSTEM_PROMPT, analysisContext);

        // Build response
        return buildAnalysisDTO(service, recentMetrics, recentHealthChecks, activeIncidents, aiResponse);
    }

    private String buildAnalysisContext(ManagedService service, List<ServiceMetrics> metrics,
                                         List<HealthCheckResult> healthChecks, List<Incident> incidents) {
        StringBuilder context = new StringBuilder();
        
        context.append("Analyze the following service data:\n\n");
        context.append("SERVICE: ").append(service.getName()).append("\n");
        context.append("Type: ").append(service.getServiceType()).append("\n");
        context.append("Environment: ").append(service.getEnvironment()).append("\n");
        context.append("Current Health: ").append(service.getHealthStatus()).append("\n");
        context.append("Running: ").append(service.getIsRunning()).append("\n\n");

        // Recent metrics summary
        if (!metrics.isEmpty()) {
            context.append("RECENT METRICS (last hour):\n");
            ServiceMetrics latest = metrics.get(0);
            
            if (latest.getCpuUsage() != null) {
                context.append("- CPU Usage: ").append(String.format("%.1f%%", latest.getCpuUsage())).append("\n");
            }
            if (latest.getMemoryUsagePercent() != null) {
                context.append("- Memory Usage: ").append(String.format("%.1f%%", latest.getMemoryUsagePercent())).append("\n");
            }
            if (latest.getErrorRate() != null) {
                context.append("- Error Rate: ").append(String.format("%.2f%%", latest.getErrorRate())).append("\n");
            }
            if (latest.getAverageResponseTime() != null) {
                context.append("- Avg Response Time: ").append(String.format("%.0fms", latest.getAverageResponseTime())).append("\n");
            }
            if (latest.getThreadCount() != null) {
                context.append("- Thread Count: ").append(latest.getThreadCount()).append("\n");
            }

            // Calculate trends
            if (metrics.size() > 1) {
                ServiceMetrics oldest = metrics.get(metrics.size() - 1);
                context.append("\nTRENDS:\n");
                
                if (latest.getCpuUsage() != null && oldest.getCpuUsage() != null) {
                    double cpuDiff = latest.getCpuUsage() - oldest.getCpuUsage();
                    context.append("- CPU trend: ").append(cpuDiff > 0 ? "↑" : "↓")
                            .append(String.format(" %.1f%%", Math.abs(cpuDiff))).append("\n");
                }
                if (latest.getMemoryUsagePercent() != null && oldest.getMemoryUsagePercent() != null) {
                    double memDiff = latest.getMemoryUsagePercent() - oldest.getMemoryUsagePercent();
                    context.append("- Memory trend: ").append(memDiff > 0 ? "↑" : "↓")
                            .append(String.format(" %.1f%%", Math.abs(memDiff))).append("\n");
                }
            }
        }

        // Health check summary
        if (!healthChecks.isEmpty()) {
            long healthy = healthChecks.stream().filter(h -> h.getStatus() == HealthStatus.HEALTHY).count();
            long total = healthChecks.size();
            double healthRate = (double) healthy / total * 100;
            
            context.append("\nHEALTH CHECKS:\n");
            context.append("- Success rate: ").append(String.format("%.0f%%", healthRate))
                    .append(" (").append(healthy).append("/").append(total).append(")\n");
            
            HealthCheckResult latest = healthChecks.get(0);
            if (latest.getResponseTimeMs() != null) {
                context.append("- Latest response time: ").append(latest.getResponseTimeMs()).append("ms\n");
            }
        }

        // Active incidents
        if (!incidents.isEmpty()) {
            context.append("\nACTIVE INCIDENTS:\n");
            for (Incident incident : incidents) {
                context.append("- [").append(incident.getSeverity()).append("] ")
                        .append(incident.getTitle()).append("\n");
            }
        }

        context.append("\nProvide: 1) Health assessment 2) Root cause analysis 3) Recommended actions");
        
        return context.toString();
    }

    private AIAnalysisDTO buildAnalysisDTO(ManagedService service, List<ServiceMetrics> metrics,
                                            List<HealthCheckResult> healthChecks, List<Incident> incidents,
                                            String aiResponse) {
        List<AIRecommendation> recommendations = new ArrayList<>();
        List<String> riskFactors = new ArrayList<>();
        int riskScore = 0;
        String riskTrend = "STABLE";
        boolean anomalyDetected = false;
        String anomalyType = null;
        String anomalyDescription = null;

        // Rule-based analysis (fallback if AI unavailable)
        if (!metrics.isEmpty()) {
            ServiceMetrics latest = metrics.get(0);

            // CPU analysis
            if (latest.getCpuUsage() != null && latest.getCpuUsage() > 80) {
                riskScore += 30;
                riskFactors.add("High CPU usage: " + String.format("%.1f%%", latest.getCpuUsage()));
                
                if (latest.getCpuUsage() > 90) {
                    anomalyDetected = true;
                    anomalyType = "CPU_SATURATION";
                    anomalyDescription = "CPU usage critically high";
                    
                    recommendations.add(AIRecommendation.builder()
                            .action("INVESTIGATE")
                            .reason("CPU usage is above 90%, indicating potential saturation")
                            .urgency("HIGH")
                            .confidence(0.9)
                            .requiresConfirmation(false)
                            .build());
                }
            }

            // Memory analysis
            if (latest.getMemoryUsagePercent() != null && latest.getMemoryUsagePercent() > 85) {
                riskScore += 25;
                riskFactors.add("High memory usage: " + String.format("%.1f%%", latest.getMemoryUsagePercent()));

                // Check for memory leak pattern
                if (metrics.size() > 5) {
                    boolean increasing = true;
                    for (int i = 1; i < Math.min(5, metrics.size()); i++) {
                        if (metrics.get(i-1).getMemoryUsagePercent() == null ||
                            metrics.get(i).getMemoryUsagePercent() == null ||
                            metrics.get(i-1).getMemoryUsagePercent() <= metrics.get(i).getMemoryUsagePercent()) {
                            increasing = false;
                            break;
                        }
                    }
                    if (increasing) {
                        anomalyDetected = true;
                        anomalyType = "MEMORY_LEAK";
                        anomalyDescription = "Memory usage consistently increasing - possible memory leak";
                        
                        recommendations.add(AIRecommendation.builder()
                                .action("RESTART")
                                .reason("Memory leak detected. Recommend restart during low traffic window.")
                                .urgency("MEDIUM")
                                .confidence(0.75)
                                .requiresConfirmation(true)
                                .build());
                    }
                }
            }

            // Error rate analysis
            if (latest.getErrorRate() != null && latest.getErrorRate() > 5) {
                riskScore += 35;
                riskFactors.add("Elevated error rate: " + String.format("%.2f%%", latest.getErrorRate()));
                
                if (latest.getErrorRate() > 10) {
                    anomalyDetected = true;
                    anomalyType = "ERROR_SPIKE";
                    anomalyDescription = "Error rate significantly above normal";
                    
                    recommendations.add(AIRecommendation.builder()
                            .action("INVESTIGATE_LOGS")
                            .reason("Error rate exceeds 10%. Check logs for root cause.")
                            .urgency("HIGH")
                            .confidence(0.85)
                            .requiresConfirmation(false)
                            .build());
                }
            }

            // Response time analysis
            if (latest.getAverageResponseTime() != null && latest.getAverageResponseTime() > 2000) {
                riskScore += 20;
                riskFactors.add("High response time: " + String.format("%.0fms", latest.getAverageResponseTime()));
                
                recommendations.add(AIRecommendation.builder()
                        .action("SCALE_UP")
                        .reason("Response time elevated. Consider scaling up instances.")
                        .urgency("MEDIUM")
                        .confidence(0.7)
                        .requiresConfirmation(true)
                        .build());
            }

            // Determine trend
            if (metrics.size() > 3) {
                double avgRecent = metrics.subList(0, 3).stream()
                        .filter(m -> m.getCpuUsage() != null)
                        .mapToDouble(ServiceMetrics::getCpuUsage)
                        .average().orElse(0);
                double avgOlder = metrics.subList(Math.max(0, metrics.size() - 3), metrics.size()).stream()
                        .filter(m -> m.getCpuUsage() != null)
                        .mapToDouble(ServiceMetrics::getCpuUsage)
                        .average().orElse(0);
                
                if (avgRecent > avgOlder * 1.2) {
                    riskTrend = "DEGRADING";
                } else if (avgRecent < avgOlder * 0.8) {
                    riskTrend = "IMPROVING";
                }
            }
        }

        // Active incidents impact
        if (!incidents.isEmpty()) {
            long criticalCount = incidents.stream()
                    .filter(i -> i.getSeverity() == IncidentSeverity.CRITICAL)
                    .count();
            if (criticalCount > 0) {
                riskScore += 30;
                riskFactors.add(criticalCount + " critical incident(s) active");
            }
        }

        // Cap risk score
        riskScore = Math.min(100, riskScore);

        // Build health assessment
        String healthAssessment;
        if (aiResponse != null && !aiResponse.isEmpty()) {
            healthAssessment = aiResponse;
        } else {
            healthAssessment = buildDefaultHealthAssessment(service, riskScore, riskFactors, anomalyDetected);
        }

        return AIAnalysisDTO.builder()
                .serviceId(service.getId())
                .serviceName(service.getName())
                .analysisTime(LocalDateTime.now())
                .healthAssessment(healthAssessment)
                .confidence(aiResponse != null ? 0.85 : 0.7)
                .riskScore(riskScore)
                .riskTrend(riskTrend)
                .riskFactors(riskFactors)
                .anomalyDetected(anomalyDetected)
                .anomalyType(anomalyType)
                .anomalyDescription(anomalyDescription)
                .recommendations(recommendations)
                .build();
    }

    private String buildDefaultHealthAssessment(ManagedService service, int riskScore,
                                                 List<String> riskFactors, boolean anomalyDetected) {
        StringBuilder assessment = new StringBuilder();
        
        assessment.append("Service ").append(service.getName())
                .append(" is currently ").append(service.getHealthStatus()).append(".\n\n");
        
        if (riskScore > 70) {
            assessment.append("⚠️ HIGH RISK: Immediate attention required.\n");
        } else if (riskScore > 40) {
            assessment.append("⚡ MODERATE RISK: Monitoring recommended.\n");
        } else {
            assessment.append("✅ LOW RISK: Operating normally.\n");
        }

        if (!riskFactors.isEmpty()) {
            assessment.append("\nRisk Factors:\n");
            for (String factor : riskFactors) {
                assessment.append("• ").append(factor).append("\n");
            }
        }

        if (anomalyDetected) {
            assessment.append("\n🔍 Anomaly detected - review recommendations.");
        }

        return assessment.toString();
    }

    public String generateIncidentSummary(Incident incident, List<ServiceMetrics> metrics) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Generate a brief incident summary for operations team:\n\n");
        prompt.append("Incident: ").append(incident.getTitle()).append("\n");
        prompt.append("Severity: ").append(incident.getSeverity()).append("\n");
        prompt.append("Service: ").append(incident.getService().getName()).append("\n");
        prompt.append("Status: ").append(incident.getStatus()).append("\n");
        
        if (incident.getDescription() != null) {
            prompt.append("Description: ").append(incident.getDescription()).append("\n");
        }

        if (!metrics.isEmpty()) {
            ServiceMetrics latest = metrics.get(0);
            prompt.append("\nCurrent Metrics:\n");
            if (latest.getCpuUsage() != null) {
                prompt.append("- CPU: ").append(String.format("%.1f%%", latest.getCpuUsage())).append("\n");
            }
            if (latest.getMemoryUsagePercent() != null) {
                prompt.append("- Memory: ").append(String.format("%.1f%%", latest.getMemoryUsagePercent())).append("\n");
            }
            if (latest.getErrorRate() != null) {
                prompt.append("- Error Rate: ").append(String.format("%.2f%%", latest.getErrorRate())).append("\n");
            }
        }

        prompt.append("\nProvide: 1) What happened 2) Impact 3) Recommended immediate action");

        String response = ollamaClient.generateChatResponse(SYSTEM_PROMPT, prompt.toString());
        
        if (response != null && !response.isEmpty()) {
            return response;
        }

        // Fallback summary
        return String.format("%s %s entered %s state. Error rate: %.2f%%. Recommended: Monitor closely and consider restart if degradation continues.",
                incident.getService().getServiceType(),
                incident.getService().getName(),
                incident.getSeverity(),
                incident.getErrorRateIncrease() != null ? incident.getErrorRateIncrease() : 0.0);
    }

    public Integer calculateStabilityScore(Long serviceId) {
        LocalDateTime since = LocalDateTime.now().minusHours(24);
        
        // Get health check success rate
        List<HealthCheckResult> healthChecks = healthCheckRepository.findRecentChecks(serviceId, since);
        long healthyChecks = healthChecks.stream()
                .filter(h -> h.getStatus() == HealthStatus.HEALTHY)
                .count();
        double healthRate = healthChecks.isEmpty() ? 100 : 
                (double) healthyChecks / healthChecks.size() * 100;

        // Get average error rate
        Double avgErrorRate = metricsRepository.getAverageErrorRate(serviceId, since);
        avgErrorRate = avgErrorRate != null ? avgErrorRate : 0.0;

        // Get incident count
        long incidentCount = incidentRepository.findRecentByService(serviceId, since).size();

        // Calculate stability score
        int score = 100;
        
        // Deduct for health check failures
        score -= (int) ((100 - healthRate) * 0.4);
        
        // Deduct for error rate
        score -= (int) (avgErrorRate * 2);
        
        // Deduct for incidents
        score -= (int) (incidentCount * 10);

        return Math.max(0, Math.min(100, score));
    }

    public boolean isAIAvailable() {
        return ollamaClient.isAvailable();
    }
}

