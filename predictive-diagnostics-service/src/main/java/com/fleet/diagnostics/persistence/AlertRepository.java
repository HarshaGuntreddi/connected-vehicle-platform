package com.fleet.diagnostics.persistence;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Spring Data repository over persisted diagnostic alerts.
 */
public interface AlertRepository extends JpaRepository<AlertEntity, Long> {

    /** Most-recent alerts for a vehicle. */
    List<AlertEntity> findByVehicleIdOrderByTsDesc(String vehicleId, Pageable pageable);

    /** Most-recent unresolved (active) alerts across all vehicles. */
    List<AlertEntity> findByResolvedFalseOrderByTsDesc(Pageable pageable);

    /** Most-recent unresolved (active) alerts for a vehicle. */
    List<AlertEntity> findByVehicleIdAndResolvedFalseOrderByTsDesc(String vehicleId, Pageable pageable);

    /** Count of currently-active alerts for a vehicle. */
    long countByVehicleIdAndResolvedFalse(String vehicleId);
}
