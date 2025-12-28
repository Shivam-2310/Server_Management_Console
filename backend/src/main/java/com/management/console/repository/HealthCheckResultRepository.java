package com.management.console.repository;

import com.management.console.domain.entity.HealthCheckResult;
import com.management.console.domain.enums.HealthStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface HealthCheckResultRepository extends JpaRepository<HealthCheckResult, Long> {

    List<HealthCheckResult> findByServiceIdOrderByTimestampDesc(Long serviceId);

    Optional<HealthCheckResult> findTopByServiceIdOrderByTimestampDesc(Long serviceId);

    List<HealthCheckResult> findByServiceIdAndTimestampBetweenOrderByTimestampAsc(
            Long serviceId, LocalDateTime start, LocalDateTime end);

    @Query("SELECT h FROM HealthCheckResult h WHERE h.service.id = :serviceId " +
           "AND h.timestamp >= :since ORDER BY h.timestamp DESC")
    List<HealthCheckResult> findRecentChecks(@Param("serviceId") Long serviceId, 
                                              @Param("since") LocalDateTime since);

    @Query("SELECT COUNT(h) FROM HealthCheckResult h WHERE h.service.id = :serviceId " +
           "AND h.status = :status AND h.timestamp >= :since")
    Long countByStatusSince(@Param("serviceId") Long serviceId, 
                            @Param("status") HealthStatus status, 
                            @Param("since") LocalDateTime since);

    @Query("SELECT h.status, COUNT(h) FROM HealthCheckResult h " +
           "WHERE h.service.id = :serviceId AND h.timestamp >= :since GROUP BY h.status")
    List<Object[]> getStatusDistribution(@Param("serviceId") Long serviceId, 
                                          @Param("since") LocalDateTime since);

    @Query("SELECT AVG(h.responseTimeMs) FROM HealthCheckResult h " +
           "WHERE h.service.id = :serviceId AND h.timestamp >= :since")
    Double getAverageResponseTime(@Param("serviceId") Long serviceId, 
                                   @Param("since") LocalDateTime since);

    @Modifying
    @Query("DELETE FROM HealthCheckResult h WHERE h.timestamp < :before")
    int deleteOldResults(@Param("before") LocalDateTime before);

    @Query("SELECT h FROM HealthCheckResult h WHERE h.service.id = :serviceId " +
           "AND h.status != 'HEALTHY' AND h.timestamp >= :since ORDER BY h.timestamp DESC")
    List<HealthCheckResult> findUnhealthyChecks(@Param("serviceId") Long serviceId, 
                                                 @Param("since") LocalDateTime since);
}

