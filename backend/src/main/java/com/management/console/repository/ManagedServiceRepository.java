package com.management.console.repository;

import com.management.console.domain.entity.ManagedService;
import com.management.console.domain.enums.HealthStatus;
import com.management.console.domain.enums.ServiceType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ManagedServiceRepository extends JpaRepository<ManagedService, Long> {

    Optional<ManagedService> findByName(String name);

    List<ManagedService> findByServiceType(ServiceType serviceType);

    List<ManagedService> findByHealthStatus(HealthStatus healthStatus);

    List<ManagedService> findByEnabledTrue();

    List<ManagedService> findByServiceTypeAndEnabledTrue(ServiceType serviceType);

    List<ManagedService> findByEnvironment(String environment);

    @Query("SELECT s FROM ManagedService s WHERE s.healthStatus IN :statuses")
    List<ManagedService> findByHealthStatusIn(@Param("statuses") List<HealthStatus> statuses);

    @Query("SELECT s FROM ManagedService s WHERE s.stabilityScore < :threshold")
    List<ManagedService> findServicesWithLowStability(@Param("threshold") Integer threshold);

    @Query("SELECT s FROM ManagedService s WHERE s.riskScore > :threshold")
    List<ManagedService> findServicesWithHighRisk(@Param("threshold") Integer threshold);

    @Query("SELECT s FROM ManagedService s WHERE s.lastHealthCheck < :threshold OR s.lastHealthCheck IS NULL")
    List<ManagedService> findServicesNeedingHealthCheck(@Param("threshold") LocalDateTime threshold);

    @Query("SELECT COUNT(s) FROM ManagedService s WHERE s.healthStatus = :status")
    Long countByHealthStatus(@Param("status") HealthStatus status);

    @Query("SELECT s.serviceType, COUNT(s) FROM ManagedService s GROUP BY s.serviceType")
    List<Object[]> countByServiceType();

    @Query("SELECT s.healthStatus, COUNT(s) FROM ManagedService s GROUP BY s.healthStatus")
    List<Object[]> getHealthDistribution();

    List<ManagedService> findByTagsContaining(String tag);

    boolean existsByName(String name);
}

