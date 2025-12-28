package com.management.console.service;

import com.management.console.domain.entity.AuditLog;
import com.management.console.domain.entity.ManagedService;
import com.management.console.domain.enums.ActionStatus;
import com.management.console.domain.enums.ServiceAction;
import com.management.console.dto.AuditLogDTO;
import com.management.console.dto.LifecycleActionRequest;
import com.management.console.mapper.ServiceMapper;
import com.management.console.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class AuditService {

    private final AuditLogRepository auditLogRepository;
    private final ServiceMapper serviceMapper;

    public AuditLog startAction(ManagedService service, LifecycleActionRequest request, 
                                String username, String ipAddress) {
        log.info("Starting audit for action {} on service {} by user {}", 
                request.getAction(), service.getName(), username);

        AuditLog auditLog = AuditLog.builder()
                .service(service)
                .timestamp(LocalDateTime.now())
                .username(username)
                .ipAddress(ipAddress)
                .action(request.getAction())
                .actionDetails(buildActionDetails(request))
                .actionStartTime(LocalDateTime.now())
                .reason(request.getReason())
                .isAutomated(false)
                .status(ActionStatus.IN_PROGRESS)
                .previousState(buildServiceState(service))
                .build();

        return auditLogRepository.save(auditLog);
    }

    public AuditLog startAutomatedAction(ManagedService service, ServiceAction action, 
                                          String automationSource, String reason) {
        log.info("Starting automated audit for action {} on service {} by {}", 
                action, service.getName(), automationSource);

        AuditLog auditLog = AuditLog.builder()
                .service(service)
                .timestamp(LocalDateTime.now())
                .username("SYSTEM")
                .action(action)
                .actionDetails("Automated action: " + action.name())
                .actionStartTime(LocalDateTime.now())
                .reason(reason)
                .isAutomated(true)
                .automationSource(automationSource)
                .status(ActionStatus.IN_PROGRESS)
                .previousState(buildServiceState(service))
                .build();

        return auditLogRepository.save(auditLog);
    }

    public void completeAction(AuditLog auditLog, ActionStatus status, String resultMessage, long durationMs) {
        auditLog.setActionEndTime(LocalDateTime.now());
        auditLog.setDurationMs(durationMs);
        auditLog.setStatus(status);
        auditLog.setResultMessage(resultMessage);
        
        if (auditLog.getService() != null) {
            auditLog.setNewState(buildServiceState(auditLog.getService()));
        }

        auditLogRepository.save(auditLog);
        
        log.info("Action {} completed with status {} in {}ms", 
                auditLog.getAction(), status, durationMs);
    }

    public void recordAIRecommendation(AuditLog auditLog, String recommendation, Double confidence) {
        auditLog.setAiRecommended(true);
        auditLog.setAiRecommendation(recommendation);
        auditLog.setAiConfidence(confidence);
        auditLogRepository.save(auditLog);
    }

    public AuditLog logHealthCheck(ManagedService service, String checkResult) {
        AuditLog auditLog = AuditLog.builder()
                .service(service)
                .timestamp(LocalDateTime.now())
                .username("SYSTEM")
                .action(ServiceAction.HEALTH_CHECK)
                .actionDetails(checkResult)
                .isAutomated(true)
                .automationSource("HealthMonitor")
                .status(ActionStatus.SUCCESS)
                .resultMessage(checkResult)
                .build();

        return auditLogRepository.save(auditLog);
    }

    public AuditLog logMetricsCollection(ManagedService service) {
        AuditLog auditLog = AuditLog.builder()
                .service(service)
                .timestamp(LocalDateTime.now())
                .username("SYSTEM")
                .action(ServiceAction.METRICS_COLLECT)
                .actionDetails("Metrics collected")
                .isAutomated(true)
                .automationSource("MetricsCollector")
                .status(ActionStatus.SUCCESS)
                .build();

        return auditLogRepository.save(auditLog);
    }

    private String buildActionDetails(LifecycleActionRequest request) {
        StringBuilder details = new StringBuilder();
        details.append("Action: ").append(request.getAction().name());
        
        if (request.getTargetInstances() != null) {
            details.append(", Target Instances: ").append(request.getTargetInstances());
        }
        if (request.getLoggerName() != null) {
            details.append(", Logger: ").append(request.getLoggerName());
            details.append(", Level: ").append(request.getLogLevel());
        }
        if (Boolean.TRUE.equals(request.getDryRun())) {
            details.append(" [DRY RUN]");
        }
        
        return details.toString();
    }

    private String buildServiceState(ManagedService service) {
        return String.format("{\"healthStatus\":\"%s\",\"isRunning\":%s,\"instanceCount\":%d}",
                service.getHealthStatus(),
                service.getIsRunning(),
                service.getInstanceCount() != null ? service.getInstanceCount() : 1);
    }

    // Query methods
    @Transactional(readOnly = true)
    public Page<AuditLogDTO> getAllAuditLogs(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return auditLogRepository.findAllByOrderByTimestampDesc(pageable)
                .map(serviceMapper::toDTO);
    }

    @Transactional(readOnly = true)
    public Page<AuditLogDTO> getAuditLogsByService(Long serviceId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return auditLogRepository.findByServiceIdOrderByTimestampDesc(serviceId, pageable)
                .map(serviceMapper::toDTO);
    }

    @Transactional(readOnly = true)
    public Page<AuditLogDTO> getAuditLogsByUser(String username, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return auditLogRepository.findByUsernameOrderByTimestampDesc(username, pageable)
                .map(serviceMapper::toDTO);
    }

    @Transactional(readOnly = true)
    public List<AuditLogDTO> getRecentActions(int hours) {
        LocalDateTime since = LocalDateTime.now().minusHours(hours);
        return auditLogRepository.findRecent(since).stream()
                .map(serviceMapper::toDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<AuditLogDTO> getRecentActionsByService(Long serviceId, int hours) {
        LocalDateTime since = LocalDateTime.now().minusHours(hours);
        return auditLogRepository.findRecentByService(serviceId, since).stream()
                .map(serviceMapper::toDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<AuditLogDTO> getFailedActions(int hours) {
        LocalDateTime since = LocalDateTime.now().minusHours(hours);
        return auditLogRepository.findFailedActions(since).stream()
                .map(serviceMapper::toDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Long countActionsSince(int hours) {
        LocalDateTime since = LocalDateTime.now().minusHours(hours);
        return auditLogRepository.countActionsSince(since);
    }
}

