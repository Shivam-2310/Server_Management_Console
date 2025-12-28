package com.management.console.repository;

import com.management.console.domain.entity.ServiceMetrics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ServiceMetricsRepository extends JpaRepository<ServiceMetrics, Long> {

    List<ServiceMetrics> findByServiceIdOrderByTimestampDesc(Long serviceId);

    List<ServiceMetrics> findByServiceIdAndTimestampBetweenOrderByTimestampAsc(
            Long serviceId, LocalDateTime start, LocalDateTime end);

    Optional<ServiceMetrics> findTopByServiceIdOrderByTimestampDesc(Long serviceId);

    @Query("SELECT m FROM ServiceMetrics m WHERE m.service.id = :serviceId " +
           "AND m.timestamp >= :since ORDER BY m.timestamp DESC")
    List<ServiceMetrics> findRecentMetrics(@Param("serviceId") Long serviceId, 
                                            @Param("since") LocalDateTime since);

    @Query("SELECT AVG(m.cpuUsage) FROM ServiceMetrics m WHERE m.service.id = :serviceId " +
           "AND m.timestamp >= :since")
    Double getAverageCpuUsage(@Param("serviceId") Long serviceId, 
                              @Param("since") LocalDateTime since);

    @Query("SELECT AVG(m.memoryUsagePercent) FROM ServiceMetrics m WHERE m.service.id = :serviceId " +
           "AND m.timestamp >= :since")
    Double getAverageMemoryUsage(@Param("serviceId") Long serviceId, 
                                  @Param("since") LocalDateTime since);

    @Query("SELECT AVG(m.averageResponseTime) FROM ServiceMetrics m WHERE m.service.id = :serviceId " +
           "AND m.timestamp >= :since")
    Double getAverageResponseTime(@Param("serviceId") Long serviceId, 
                                   @Param("since") LocalDateTime since);

    @Query("SELECT AVG(m.errorRate) FROM ServiceMetrics m WHERE m.service.id = :serviceId " +
           "AND m.timestamp >= :since")
    Double getAverageErrorRate(@Param("serviceId") Long serviceId, 
                                @Param("since") LocalDateTime since);

    @Modifying
    @Query("DELETE FROM ServiceMetrics m WHERE m.timestamp < :before")
    int deleteOldMetrics(@Param("before") LocalDateTime before);

    @Query("SELECT m FROM ServiceMetrics m WHERE m.service.id = :serviceId " +
           "AND m.timestamp >= :since AND (m.cpuUsage > :cpuThreshold OR m.errorRate > :errorThreshold)")
    List<ServiceMetrics> findAnomalousMetrics(@Param("serviceId") Long serviceId,
                                               @Param("since") LocalDateTime since,
                                               @Param("cpuThreshold") Double cpuThreshold,
                                               @Param("errorThreshold") Double errorThreshold);
}

