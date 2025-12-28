package com.management.console.controller;

import com.management.console.dto.AuditLogDTO;
import com.management.console.service.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/audit")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class AuditController {

    private final AuditService auditService;

    @GetMapping
    public ResponseEntity<Page<AuditLogDTO>> getAllAuditLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(auditService.getAllAuditLogs(page, size));
    }

    @GetMapping("/service/{serviceId}")
    public ResponseEntity<Page<AuditLogDTO>> getAuditLogsByService(
            @PathVariable Long serviceId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(auditService.getAuditLogsByService(serviceId, page, size));
    }

    @GetMapping("/user/{username}")
    public ResponseEntity<Page<AuditLogDTO>> getAuditLogsByUser(
            @PathVariable String username,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(auditService.getAuditLogsByUser(username, page, size));
    }

    @GetMapping("/recent")
    public ResponseEntity<List<AuditLogDTO>> getRecentActions(
            @RequestParam(defaultValue = "24") int hours) {
        return ResponseEntity.ok(auditService.getRecentActions(hours));
    }

    @GetMapping("/service/{serviceId}/recent")
    public ResponseEntity<List<AuditLogDTO>> getRecentActionsByService(
            @PathVariable Long serviceId,
            @RequestParam(defaultValue = "24") int hours) {
        return ResponseEntity.ok(auditService.getRecentActionsByService(serviceId, hours));
    }

    @GetMapping("/failed")
    public ResponseEntity<List<AuditLogDTO>> getFailedActions(
            @RequestParam(defaultValue = "24") int hours) {
        return ResponseEntity.ok(auditService.getFailedActions(hours));
    }

    @GetMapping("/count/today")
    public ResponseEntity<Long> countActionsToday() {
        return ResponseEntity.ok(auditService.countActionsSince(24));
    }
}

