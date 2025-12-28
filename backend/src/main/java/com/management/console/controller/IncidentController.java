package com.management.console.controller;

import com.management.console.domain.enums.IncidentSeverity;
import com.management.console.dto.IncidentDTO;
import com.management.console.service.IncidentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/incidents")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class IncidentController {

    private final IncidentService incidentService;

    @GetMapping
    public ResponseEntity<Page<IncidentDTO>> getAllIncidents(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(incidentService.getAllIncidents(page, size));
    }

    @GetMapping("/active")
    public ResponseEntity<List<IncidentDTO>> getActiveIncidents() {
        return ResponseEntity.ok(incidentService.getActiveIncidents());
    }

    @GetMapping("/active/count")
    public ResponseEntity<Long> countActiveIncidents() {
        return ResponseEntity.ok(incidentService.countActiveIncidents());
    }

    @GetMapping("/service/{serviceId}")
    public ResponseEntity<List<IncidentDTO>> getIncidentsByService(@PathVariable Long serviceId) {
        return ResponseEntity.ok(incidentService.getIncidentsByService(serviceId));
    }

    @GetMapping("/service/{serviceId}/active")
    public ResponseEntity<List<IncidentDTO>> getActiveIncidentsByService(@PathVariable Long serviceId) {
        return ResponseEntity.ok(incidentService.getActiveIncidentsByService(serviceId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<IncidentDTO> getIncident(@PathVariable Long id) {
        return ResponseEntity.ok(incidentService.getIncident(id));
    }

    @PostMapping("/{id}/acknowledge")
    public ResponseEntity<IncidentDTO> acknowledgeIncident(
            @PathVariable Long id,
            Principal principal) {
        String username = principal != null ? principal.getName() : "anonymous";
        log.info("Acknowledging incident {} by user {}", id, username);
        return ResponseEntity.ok(incidentService.acknowledgeIncident(id, username));
    }

    @PostMapping("/{id}/resolve")
    public ResponseEntity<IncidentDTO> resolveIncident(
            @PathVariable Long id,
            @RequestParam String resolution,
            Principal principal) {
        String username = principal != null ? principal.getName() : "anonymous";
        log.info("Resolving incident {} by user {} with resolution: {}", id, username, resolution);
        return ResponseEntity.ok(incidentService.resolveIncident(id, username, resolution));
    }

    @PostMapping("/{id}/close")
    public ResponseEntity<IncidentDTO> closeIncident(@PathVariable Long id) {
        log.info("Closing incident {}", id);
        return ResponseEntity.ok(incidentService.closeIncident(id));
    }

    @PatchMapping("/{id}/severity")
    public ResponseEntity<IncidentDTO> updateSeverity(
            @PathVariable Long id,
            @RequestParam IncidentSeverity severity) {
        log.info("Updating incident {} severity to {}", id, severity);
        return ResponseEntity.ok(incidentService.updateIncidentSeverity(id, severity));
    }

    @GetMapping("/count/today")
    public ResponseEntity<Long> countIncidentsToday() {
        return ResponseEntity.ok(incidentService.countIncidentsSince(24));
    }
}

