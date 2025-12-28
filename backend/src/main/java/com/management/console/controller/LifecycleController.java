package com.management.console.controller;

import com.management.console.dto.LifecycleActionRequest;
import com.management.console.dto.LifecycleActionResponse;
import com.management.console.service.LifecycleControllerService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
@RequestMapping("/api/lifecycle")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class LifecycleController {

    private final LifecycleControllerService lifecycleService;

    @PostMapping("/action")
    public ResponseEntity<LifecycleActionResponse> executeAction(
            @Valid @RequestBody LifecycleActionRequest request,
            HttpServletRequest httpRequest,
            Principal principal) {
        
        String username = principal != null ? principal.getName() : "anonymous";
        String ipAddress = getClientIpAddress(httpRequest);
        
        log.info("Lifecycle action requested: {} for service {} by user {} from IP {}", 
                request.getAction(), request.getServiceId(), username, ipAddress);
        
        LifecycleActionResponse response = lifecycleService.executeAction(request, username, ipAddress);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/start/{serviceId}")
    public ResponseEntity<LifecycleActionResponse> startService(
            @PathVariable Long serviceId,
            @RequestParam(required = false) String reason,
            @RequestParam(defaultValue = "false") Boolean dryRun,
            HttpServletRequest httpRequest,
            Principal principal) {
        
        LifecycleActionRequest request = LifecycleActionRequest.builder()
                .serviceId(serviceId)
                .action(com.management.console.domain.enums.ServiceAction.START)
                .reason(reason)
                .dryRun(dryRun)
                .confirmed(true)
                .build();
        
        String username = principal != null ? principal.getName() : "anonymous";
        String ipAddress = getClientIpAddress(httpRequest);
        
        return ResponseEntity.ok(lifecycleService.executeAction(request, username, ipAddress));
    }

    @PostMapping("/stop/{serviceId}")
    public ResponseEntity<LifecycleActionResponse> stopService(
            @PathVariable Long serviceId,
            @RequestParam(required = false) String reason,
            @RequestParam(defaultValue = "false") Boolean dryRun,
            @RequestParam(defaultValue = "false") Boolean confirmed,
            HttpServletRequest httpRequest,
            Principal principal) {
        
        LifecycleActionRequest request = LifecycleActionRequest.builder()
                .serviceId(serviceId)
                .action(com.management.console.domain.enums.ServiceAction.STOP)
                .reason(reason)
                .dryRun(dryRun)
                .confirmed(confirmed)
                .build();
        
        String username = principal != null ? principal.getName() : "anonymous";
        String ipAddress = getClientIpAddress(httpRequest);
        
        return ResponseEntity.ok(lifecycleService.executeAction(request, username, ipAddress));
    }

    @PostMapping("/restart/{serviceId}")
    public ResponseEntity<LifecycleActionResponse> restartService(
            @PathVariable Long serviceId,
            @RequestParam(required = false) String reason,
            @RequestParam(defaultValue = "false") Boolean dryRun,
            @RequestParam(defaultValue = "false") Boolean confirmed,
            HttpServletRequest httpRequest,
            Principal principal) {
        
        LifecycleActionRequest request = LifecycleActionRequest.builder()
                .serviceId(serviceId)
                .action(com.management.console.domain.enums.ServiceAction.RESTART)
                .reason(reason)
                .dryRun(dryRun)
                .confirmed(confirmed)
                .build();
        
        String username = principal != null ? principal.getName() : "anonymous";
        String ipAddress = getClientIpAddress(httpRequest);
        
        return ResponseEntity.ok(lifecycleService.executeAction(request, username, ipAddress));
    }

    @PostMapping("/scale-up/{serviceId}")
    public ResponseEntity<LifecycleActionResponse> scaleUp(
            @PathVariable Long serviceId,
            @RequestParam(required = false) Integer targetInstances,
            @RequestParam(required = false) String reason,
            HttpServletRequest httpRequest,
            Principal principal) {
        
        LifecycleActionRequest request = LifecycleActionRequest.builder()
                .serviceId(serviceId)
                .action(com.management.console.domain.enums.ServiceAction.SCALE_UP)
                .targetInstances(targetInstances)
                .reason(reason)
                .confirmed(true)
                .build();
        
        String username = principal != null ? principal.getName() : "anonymous";
        String ipAddress = getClientIpAddress(httpRequest);
        
        return ResponseEntity.ok(lifecycleService.executeAction(request, username, ipAddress));
    }

    @PostMapping("/scale-down/{serviceId}")
    public ResponseEntity<LifecycleActionResponse> scaleDown(
            @PathVariable Long serviceId,
            @RequestParam(required = false) Integer targetInstances,
            @RequestParam(required = false) String reason,
            @RequestParam(defaultValue = "false") Boolean confirmed,
            HttpServletRequest httpRequest,
            Principal principal) {
        
        LifecycleActionRequest request = LifecycleActionRequest.builder()
                .serviceId(serviceId)
                .action(com.management.console.domain.enums.ServiceAction.SCALE_DOWN)
                .targetInstances(targetInstances)
                .reason(reason)
                .confirmed(confirmed)
                .build();
        
        String username = principal != null ? principal.getName() : "anonymous";
        String ipAddress = getClientIpAddress(httpRequest);
        
        return ResponseEntity.ok(lifecycleService.executeAction(request, username, ipAddress));
    }

    @GetMapping("/restart-count/{serviceId}")
    public ResponseEntity<Long> getRecentRestartCount(
            @PathVariable Long serviceId,
            @RequestParam(defaultValue = "24") int hours) {
        return ResponseEntity.ok(lifecycleService.countRecentRestarts(serviceId, hours));
    }

    /**
     * Check if a service process is running
     */
    @GetMapping("/status/{serviceId}")
    public ResponseEntity<ProcessStatusResponse> getProcessStatus(@PathVariable Long serviceId) {
        boolean isRunning = lifecycleService.isServiceRunning(serviceId);
        Long pid = lifecycleService.getProcessId(serviceId);
        
        return ResponseEntity.ok(ProcessStatusResponse.builder()
                .serviceId(serviceId)
                .isRunning(isRunning)
                .pid(pid)
                .status(isRunning ? "RUNNING" : "STOPPED")
                .build());
    }

    /**
     * Get PID of a running service
     */
    @GetMapping("/pid/{serviceId}")
    public ResponseEntity<Long> getProcessId(@PathVariable Long serviceId) {
        Long pid = lifecycleService.getProcessId(serviceId);
        if (pid != null) {
            return ResponseEntity.ok(pid);
        }
        return ResponseEntity.notFound().build();
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ProcessStatusResponse {
        private Long serviceId;
        private boolean isRunning;
        private Long pid;
        private String status;
    }

    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}

