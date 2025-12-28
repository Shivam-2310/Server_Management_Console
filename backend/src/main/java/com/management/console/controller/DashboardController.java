package com.management.console.controller;

import com.management.console.dto.DashboardDTO;
import com.management.console.service.DashboardService;
import com.management.console.service.ai.AIIntelligenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class DashboardController {

    private final DashboardService dashboardService;
    private final AIIntelligenceService aiService;

    @GetMapping
    public ResponseEntity<DashboardDTO> getDashboard() {
        return ResponseEntity.ok(dashboardService.getDashboardSummary());
    }

    @GetMapping("/health-distribution")
    public ResponseEntity<Map<String, Long>> getHealthDistribution() {
        return ResponseEntity.ok(dashboardService.getHealthDistribution());
    }

    @GetMapping("/service-type-distribution")
    public ResponseEntity<Map<String, Long>> getServiceTypeDistribution() {
        return ResponseEntity.ok(dashboardService.getServiceTypeDistribution());
    }

    @GetMapping("/ai-status")
    public ResponseEntity<Map<String, Object>> getAIStatus() {
        boolean available = aiService.isAIAvailable();
        return ResponseEntity.ok(Map.of(
                "available", available,
                "model", "llama3.2:1b",
                "provider", "Ollama"
        ));
    }
}

