package com.fleet.storage.service;

import com.fleet.storage.domain.TelemetrySample;
import com.fleet.storage.repo.TelemetryRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Query-side operations over stored telemetry: windowed sample lookups,
 * latest-per-signal reduction, and vehicle enumeration.
 */
@Service
public class TelemetryService {

    /** Number of recent rows scanned when reducing to latest-per-signal. */
    private static final int LATEST_SCAN_ROWS = 500;

    private final TelemetryRepository repository;

    public TelemetryService(TelemetryRepository repository) {
        this.repository = repository;
    }

    /**
     * Return samples for a vehicle+signal within [from, to], newest first, capped
     * at {@code limit} rows.
     */
    public List<TelemetrySample> querySamples(String vehicleId, String signal,
                                              Instant from, Instant to, int limit) {
        return repository.findByVehicleIdAndSignalAndTsBetweenOrderByTsDesc(
                vehicleId, signal, from, to, PageRequest.of(0, limit));
    }

    /**
     * Latest sample per signal for a vehicle. Scans the most recent rows and keeps
     * the first (newest) occurrence of each signal.
     */
    public Map<String, LatestValue> latestPerSignal(String vehicleId) {
        List<TelemetrySample> recent =
                repository.findRecentByVehicle(vehicleId, PageRequest.of(0, LATEST_SCAN_ROWS));
        Map<String, LatestValue> latest = new LinkedHashMap<>();
        for (TelemetrySample s : recent) {
            // rows arrive newest-first, so the first seen per signal is the latest
            latest.putIfAbsent(s.getSignal(), new LatestValue(s.getValue(), s.getTs()));
        }
        return latest;
    }

    /**
     * Distinct vehicle ids known to the store.
     */
    public List<String> vehicles() {
        return repository.findDistinctVehicleIds();
    }

    /** Latest value + timestamp for a signal. */
    public record LatestValue(double value, Instant ts) {
    }
}
