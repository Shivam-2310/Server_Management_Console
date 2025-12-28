package com.management.console.repository;

import com.management.console.domain.entity.AuditLog;
import com.management.console.domain.enums.ActionStatus;
import com.management.console.domain.enums.ServiceAction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    Page<AuditLog> findAllByOrderByTimestampDesc(Pageable pageable);

    List<AuditLog> findByServiceIdOrderByTimestampDesc(Long serviceId);

    Page<AuditLog> findByServiceIdOrderByTimestampDesc(Long serviceId, Pageable pageable);

    List<AuditLog> findByUsernameOrderByTimestampDesc(String username);

    Page<AuditLog> findByUsernameOrderByTimestampDesc(String username, Pageable pageable);

    List<AuditLog> findByActionOrderByTimestampDesc(ServiceAction action);

    List<AuditLog> findByStatusOrderByTimestampDesc(ActionStatus status);

    @Query("SELECT a FROM AuditLog a WHERE a.timestamp >= :since ORDER BY a.timestamp DESC")
    List<AuditLog> findRecent(@Param("since") LocalDateTime since);

    @Query("SELECT a FROM AuditLog a WHERE a.service.id = :serviceId AND a.timestamp >= :since " +
           "ORDER BY a.timestamp DESC")
    List<AuditLog> findRecentByService(@Param("serviceId") Long serviceId, 
                                        @Param("since") LocalDateTime since);

    @Query("SELECT COUNT(a) FROM AuditLog a WHERE a.timestamp >= :since")
    Long countActionsSince(@Param("since") LocalDateTime since);

    @Query("SELECT a.action, COUNT(a) FROM AuditLog a WHERE a.timestamp >= :since GROUP BY a.action")
    List<Object[]> getActionDistribution(@Param("since") LocalDateTime since);

    @Query("SELECT a FROM AuditLog a WHERE a.status = 'FAILED' AND a.timestamp >= :since " +
           "ORDER BY a.timestamp DESC")
    List<AuditLog> findFailedActions(@Param("since") LocalDateTime since);

    @Query("SELECT a FROM AuditLog a WHERE a.service.id = :serviceId " +
           "AND a.action = :action ORDER BY a.timestamp DESC")
    List<AuditLog> findByServiceAndAction(@Param("serviceId") Long serviceId, 
                                           @Param("action") ServiceAction action);

    @Query("SELECT COUNT(a) FROM AuditLog a WHERE a.service.id = :serviceId " +
           "AND a.action = 'RESTART' AND a.timestamp >= :since")
    Long countRestartsSince(@Param("serviceId") Long serviceId, 
                            @Param("since") LocalDateTime since);
}

