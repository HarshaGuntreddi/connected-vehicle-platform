package com.fleet.storage.repo;

import com.fleet.storage.domain.TelemetrySample;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

/**
 * Data access for {@link TelemetrySample} time-series rows.
 */
public interface TelemetryRepository extends JpaRepository<TelemetrySample, Long> {

    /**
     * Samples for one vehicle+signal within a time window, newest first.
     */
    List<TelemetrySample> findByVehicleIdAndSignalAndTsBetweenOrderByTsDesc(
            String vehicleId, String signal, Instant from, Instant to, Pageable pageable);

    /**
     * Distinct vehicle ids that have at least one stored sample.
     */
    @Query("select distinct t.vehicleId from TelemetrySample t")
    List<String> findDistinctVehicleIds();

    /**
     * Most recent rows for a vehicle (across all signals), newest first. Callers
     * combine this with a {@link Pageable} limit and dedupe by signal to derive
     * the latest value per signal.
     */
    @Query("select t from TelemetrySample t where t.vehicleId = :v order by t.ts desc")
    List<TelemetrySample> findRecentByVehicle(@Param("v") String vehicleId, Pageable pageable);
}
