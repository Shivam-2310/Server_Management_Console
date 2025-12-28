package com.management.console.service;

import com.management.console.domain.entity.AuditLog;
import com.management.console.domain.entity.ManagedService;
import com.management.console.domain.enums.ActionStatus;
import com.management.console.domain.enums.HealthStatus;
import com.management.console.domain.enums.ServiceAction;
import com.management.console.dto.LifecycleActionRequest;
import com.management.console.dto.LifecycleActionResponse;
import com.management.console.exception.LifecycleActionException;
import com.management.console.repository.AuditLogRepository;
import com.management.console.repository.ManagedServiceRepository;
import com.management.console.service.ProcessManagerService.ProcessResult;
import com.management.console.service.ProcessManagerService.ProcessStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class LifecycleControllerService {

    private final ManagedServiceRepository serviceRepository;
    private final AuditLogRepository auditLogRepository;
    private final AuditService auditService;
    private final ProcessManagerService processManager;

    @Value("${app.lifecycle.max-restart-attempts:3}")
    private int maxRestartAttempts;

    @Value("${app.lifecycle.restart-cooldown-seconds:60}")
    private int restartCooldownSeconds;

    // Whitelisted commands - MVP safety constraint
    private static final List<String> ALLOWED_COMMAND_PREFIXES = List.of(
            "java", "node", "npm", "yarn", "python", "python3",
            "mvn", "gradle", "./gradlew", "docker", "systemctl",
            "pm2", "nginx", "serve", "dotnet", "go", "ruby",
            "php", "python.exe", "node.exe", "java.exe"
    );

    @Transactional
    public LifecycleActionResponse executeAction(LifecycleActionRequest request, String username, String ipAddress) {
        log.info("Executing lifecycle action: {} for service {} by user {}", 
                request.getAction(), request.getServiceId(), username);

        ManagedService service = serviceRepository.findById(request.getServiceId())
                .orElseThrow(() -> new LifecycleActionException("Service not found: " + request.getServiceId()));

        // Validate action
        validateAction(service, request);

        // Check if confirmation is required for high-risk actions
        if (requiresConfirmation(request) && !Boolean.TRUE.equals(request.getConfirmed())) {
            return LifecycleActionResponse.builder()
                    .serviceId(service.getId())
                    .serviceName(service.getName())
                    .action(request.getAction())
                    .status(ActionStatus.PENDING)
                    .requiresConfirmation(true)
                    .riskLevel(calculateRiskLevel(service, request.getAction()))
                    .message("This action requires confirmation. Please confirm to proceed.")
                    .timestamp(LocalDateTime.now())
                    .build();
        }

        // Dry run mode
        if (Boolean.TRUE.equals(request.getDryRun())) {
            return LifecycleActionResponse.builder()
                    .serviceId(service.getId())
                    .serviceName(service.getName())
                    .action(request.getAction())
                    .status(ActionStatus.SUCCESS)
                    .message("Dry run completed successfully. No actual changes made.")
                    .timestamp(LocalDateTime.now())
                    .riskLevel(calculateRiskLevel(service, request.getAction()))
                    .build();
        }

        // Execute the action
        long startTime = System.currentTimeMillis();
        AuditLog auditLog = auditService.startAction(service, request, username, ipAddress);

        try {
            String result = switch (request.getAction()) {
                case START -> executeStart(service);
                case STOP -> executeStop(service);
                case RESTART -> executeRestart(service);
                case SCALE_UP -> executeScaleUp(service, request.getTargetInstances());
                case SCALE_DOWN -> executeScaleDown(service, request.getTargetInstances());
                default -> throw new LifecycleActionException("Unsupported action: " + request.getAction());
            };

            long duration = System.currentTimeMillis() - startTime;
            auditService.completeAction(auditLog, ActionStatus.SUCCESS, result, duration);

            return LifecycleActionResponse.builder()
                    .actionId(auditLog.getId())
                    .serviceId(service.getId())
                    .serviceName(service.getName())
                    .action(request.getAction())
                    .status(ActionStatus.SUCCESS)
                    .message(result)
                    .timestamp(LocalDateTime.now())
                    .durationMs(duration)
                    .build();

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            auditService.completeAction(auditLog, ActionStatus.FAILED, e.getMessage(), duration);
            
            throw new LifecycleActionException("Action failed: " + e.getMessage(), e);
        }
    }

    private void validateAction(ManagedService service, LifecycleActionRequest request) {
        // Check if service is enabled
        if (!service.getEnabled()) {
            throw new LifecycleActionException("Service is disabled and cannot be controlled");
        }

        // Check restart cooldown
        if (request.getAction() == ServiceAction.RESTART && service.getLastRestart() != null) {
            LocalDateTime cooldownEnd = service.getLastRestart().plusSeconds(restartCooldownSeconds);
            if (LocalDateTime.now().isBefore(cooldownEnd)) {
                long remainingSeconds = java.time.Duration.between(LocalDateTime.now(), cooldownEnd).getSeconds();
                throw new LifecycleActionException(
                    String.format("Restart cooldown in effect. Please wait %d seconds before restarting again.", remainingSeconds));
            }
        }

        // Validate required commands exist for START
        if (request.getAction() == ServiceAction.START) {
            if (service.getStartCommand() == null || service.getStartCommand().isEmpty()) {
                throw new LifecycleActionException("No start command configured for this service");
            }
            validateCommandSafety(service.getStartCommand());
        }

        // For STOP and RESTART, we can work with either command or PID
        if (request.getAction() == ServiceAction.STOP) {
            Long pid = processManager.getProcessId(service);
            if (pid == null && (service.getStopCommand() == null || service.getStopCommand().isEmpty())) {
                throw new LifecycleActionException("Service is not running and no stop command configured");
            }
        }
    }

    private void validateCommandSafety(String command) {
        String trimmedCommand = command.trim().toLowerCase();
        boolean isAllowed = ALLOWED_COMMAND_PREFIXES.stream()
                .anyMatch(prefix -> trimmedCommand.startsWith(prefix.toLowerCase()));
        
        if (!isAllowed) {
            throw new LifecycleActionException("Command not in allowed list for MVP safety. Contact admin to whitelist.");
        }

        // Check for dangerous patterns
        if (command.contains("rm -rf") || command.contains("format") || 
            command.contains("del /") || command.contains("rmdir /s")) {
            throw new LifecycleActionException("Command contains potentially dangerous patterns");
        }
    }

    private boolean requiresConfirmation(LifecycleActionRequest request) {
        return request.getAction() == ServiceAction.STOP || 
               request.getAction() == ServiceAction.RESTART ||
               request.getAction() == ServiceAction.SCALE_DOWN;
    }

    private Integer calculateRiskLevel(ManagedService service, ServiceAction action) {
        int risk = 0;
        
        // Base risk by action type
        risk += switch (action) {
            case STOP -> 70;
            case RESTART -> 50;
            case SCALE_DOWN -> 40;
            case START -> 20;
            case SCALE_UP -> 10;
            default -> 0;
        };

        // Adjust for environment
        if ("PROD".equalsIgnoreCase(service.getEnvironment())) {
            risk += 20;
        } else if ("STAGING".equalsIgnoreCase(service.getEnvironment())) {
            risk += 10;
        }

        // Adjust for current health
        if (service.getHealthStatus() == HealthStatus.CRITICAL) {
            risk += 10;
        }

        return Math.min(100, risk);
    }

    /**
     * Start a service
     */
    private String executeStart(ManagedService service) {
        log.info("Starting service: {}", service.getName());
        
        // Check if already running
        if (processManager.isProcessRunning(service)) {
            Long pid = processManager.getProcessId(service);
            updateServiceState(service, true, HealthStatus.UNKNOWN, pid);
            return String.format("Service is already running with PID: %d", pid);
        }
        
        // Start the process
        ProcessResult result = processManager.startProcess(service);
        
        if (result.isSuccess()) {
            updateServiceState(service, true, HealthStatus.UNKNOWN, result.getPid());
            return String.format("Service started successfully with PID: %d. %s", 
                    result.getPid(), result.getOutput() != null ? result.getOutput() : "");
        } else {
            throw new LifecycleActionException("Failed to start service: " + result.getMessage());
        }
    }

    /**
     * Stop a service
     */
    private String executeStop(ManagedService service) {
        log.info("Stopping service: {}", service.getName());
        
        // Check if running
        if (!processManager.isProcessRunning(service)) {
            updateServiceState(service, false, HealthStatus.DOWN, null);
            return "Service was not running";
        }
        
        Long pid = processManager.getProcessId(service);
        ProcessResult result = processManager.stopProcess(service);
        
        if (result.isSuccess()) {
            updateServiceState(service, false, HealthStatus.DOWN, null);
            return String.format("Service stopped successfully (was PID: %d)", pid);
        } else {
            throw new LifecycleActionException("Failed to stop service: " + result.getMessage());
        }
    }

    /**
     * Restart a service
     */
    private String executeRestart(ManagedService service) {
        log.info("Restarting service: {}", service.getName());
        
        Long oldPid = processManager.getProcessId(service);
        ProcessResult result = processManager.restartProcess(service);
        
        if (result.isSuccess()) {
            updateServiceState(service, true, HealthStatus.UNKNOWN, result.getPid());
            service.setLastRestart(LocalDateTime.now());
            serviceRepository.save(service);
            
            String message = String.format("Service restarted successfully. New PID: %d", result.getPid());
            if (oldPid != null) {
                message = String.format("Service restarted successfully. Old PID: %d -> New PID: %d", 
                        oldPid, result.getPid());
            }
            return message;
        } else {
            throw new LifecycleActionException("Failed to restart service: " + result.getMessage());
        }
    }

    /**
     * Scale up instances
     */
    private String executeScaleUp(ManagedService service, Integer targetInstances) {
        log.info("Scaling up service {} to {} instances", service.getName(), targetInstances);
        
        int currentInstances = service.getInstanceCount() != null ? service.getInstanceCount() : 1;
        int newInstances = targetInstances != null ? targetInstances : currentInstances + 1;
        
        if (newInstances <= currentInstances) {
            throw new LifecycleActionException("Target instances must be greater than current: " + currentInstances);
        }
        
        service.setInstanceCount(newInstances);
        serviceRepository.save(service);
        
        return String.format("Scaled up from %d to %d instances. Note: In MVP, this updates the count but doesn't spawn actual instances.", 
                currentInstances, newInstances);
    }

    /**
     * Scale down instances
     */
    private String executeScaleDown(ManagedService service, Integer targetInstances) {
        log.info("Scaling down service {} to {} instances", service.getName(), targetInstances);
        
        int currentInstances = service.getInstanceCount() != null ? service.getInstanceCount() : 1;
        int newInstances = targetInstances != null ? targetInstances : Math.max(1, currentInstances - 1);
        
        if (newInstances < 1) {
            throw new LifecycleActionException("Cannot scale below 1 instance");
        }
        
        if (newInstances >= currentInstances) {
            throw new LifecycleActionException("Target instances must be less than current: " + currentInstances);
        }
        
        service.setInstanceCount(newInstances);
        serviceRepository.save(service);
        
        return String.format("Scaled down from %d to %d instances. Note: In MVP, this updates the count but doesn't terminate actual instances.", 
                currentInstances, newInstances);
    }

    /**
     * Update service state in database
     */
    private void updateServiceState(ManagedService service, boolean isRunning, HealthStatus healthStatus, Long pid) {
        service.setIsRunning(isRunning);
        service.setHealthStatus(healthStatus);
        if (pid != null) {
            service.setProcessIdentifier(String.valueOf(pid));
        } else {
            service.setProcessIdentifier(null);
        }
        serviceRepository.save(service);
    }

    /**
     * Check if a service is running
     */
    public boolean isServiceRunning(Long serviceId) {
        ManagedService service = serviceRepository.findById(serviceId)
                .orElseThrow(() -> new LifecycleActionException("Service not found: " + serviceId));
        return processManager.isProcessRunning(service);
    }

    /**
     * Get process status for a service
     */
    public ProcessStatus getProcessStatus(Long serviceId) {
        ManagedService service = serviceRepository.findById(serviceId)
                .orElseThrow(() -> new LifecycleActionException("Service not found: " + serviceId));
        return processManager.getProcessStatus(service);
    }

    /**
     * Get PID for a service
     */
    public Long getProcessId(Long serviceId) {
        ManagedService service = serviceRepository.findById(serviceId)
                .orElseThrow(() -> new LifecycleActionException("Service not found: " + serviceId));
        return processManager.getProcessId(service);
    }

    @Transactional(readOnly = true)
    public Long countRecentRestarts(Long serviceId, int hours) {
        LocalDateTime since = LocalDateTime.now().minusHours(hours);
        return auditLogRepository.countRestartsSince(serviceId, since);
    }
}
