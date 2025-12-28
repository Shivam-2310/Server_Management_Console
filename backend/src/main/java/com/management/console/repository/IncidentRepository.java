package com.management.console.repository;

import com.management.console.domain.entity.Incident;
import com.management.console.domain.enums.IncidentSeverity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface IncidentRepository extends JpaRepository<Incident, Long> {

    Page<Incident> findAllByOrderByCreatedAtDesc(Pageable pageable);

    List<Incident> findByServiceIdOrderByCreatedAtDesc(Long serviceId);

    List<Incident> findByStatus(String status);

    List<Incident> findByStatusIn(List<String> statuses);

    List<Incident> findBySeverity(IncidentSeverity severity);

    @Query("SELECT i FROM Incident i WHERE i.status IN ('OPEN', 'INVESTIGATING') " +
           "ORDER BY i.severity DESC, i.createdAt DESC")
    List<Incident> findActiveIncidents();

    @Query("SELECT i FROM Incident i WHERE i.service.id = :serviceId " +
           "AND i.status IN ('OPEN', 'INVESTIGATING') ORDER BY i.createdAt DESC")
    List<Incident> findActiveIncidentsByService(@Param("serviceId") Long serviceId);

    @Query("SELECT COUNT(i) FROM Incident i WHERE i.status IN ('OPEN', 'INVESTIGATING')")
    Long countActiveIncidents();

    @Query("SELECT i.severity, COUNT(i) FROM Incident i WHERE i.status IN ('OPEN', 'INVESTIGATING') " +
           "GROUP BY i.severity")
    List<Object[]> getActiveSeverityDistribution();

    @Query("SELECT i FROM Incident i WHERE i.createdAt >= :since ORDER BY i.createdAt DESC")
    List<Incident> findRecentIncidents(@Param("since") LocalDateTime since);

    @Query("SELECT COUNT(i) FROM Incident i WHERE i.createdAt >= :since")
    Long countIncidentsSince(@Param("since") LocalDateTime since);

    @Query("SELECT i FROM Incident i WHERE i.service.id = :serviceId " +
           "AND i.createdAt >= :since ORDER BY i.createdAt DESC")
    List<Incident> findRecentByService(@Param("serviceId") Long serviceId, 
                                        @Param("since") LocalDateTime since);

    @Query("SELECT i.detectionSource, COUNT(i) FROM Incident i " +
           "WHERE i.createdAt >= :since GROUP BY i.detectionSource")
    List<Object[]> getDetectionSourceDistribution(@Param("since") LocalDateTime since);
}

