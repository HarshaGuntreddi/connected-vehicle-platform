package com.fleet.analytics.service;

import com.fleet.common.dto.DiagnosticAlert;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * Holds the in-memory, thread-safe aggregation state for the fleet.
 *
 * <p>Two sources feed this aggregator:
 * <ul>
 *   <li>Kafka {@code diagnostic-alerts} events, folded into per-vehicle / per-type
 *       counters via {@link #onAlert(DiagnosticAlert)}.</li>
 *   <li>A scheduled poll of the diagnostics service that computes fleet-wide health
 *       and active-alert figures via {@link #pollDiagnostics()}.</li>
 * </ul>
 *
 * <p>All mutable state is either a concurrent collection, a {@link LongAdder}, or a
 * {@code volatile} primitive, so it can safely be read by the REST layer and the
 * Micrometer gauge suppliers while being updated by the Kafka and scheduler threads.
 */
@Service
public class FleetAggregator {

    private static final Logger log = LoggerFactory.getLogger(FleetAggregator.class);

    /** Alert type that carries a diagnostic trouble / fault code. */
    private static final String FAULT_CODE_TYPE = "FAULT_CODE";

    // --- Alert-derived counters (updated by the Kafka listener) -------------
    private final ConcurrentHashMap<String, LongAdder> alertsPerVehicle = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, LongAdder> alertsByType = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, LongAdder> topFaults = new ConcurrentHashMap<>();
    private final LongAdder totalAlerts = new LongAdder();

    // --- Poll-derived fleet snapshot (updated by the scheduled task) --------
    private volatile double fleetAvgHealth = 0.0;
    private volatile int vehicleCount = 0;
    private volatile int activeAlerts = 0;

    private final RestClient restClient;
    private final MeterRegistry registry;
    private final String diagnosticsUrl;

    public FleetAggregator(RestClient restClient,
                           MeterRegistry registry,
                           @Value("${services.diagnostics-url:http://localhost:8084}") String diagnosticsUrl) {
        this.restClient = restClient;
        this.registry = registry;
        this.diagnosticsUrl = diagnosticsUrl;
    }

    /**
     * Registers the fleet-level Micrometer gauges. Each gauge is backed by a supplier
     * that reads the corresponding {@code volatile} field, so Prometheus always scrapes
     * the latest polled value.
     */
    @PostConstruct
    void registerGauges() {
        Gauge.builder("fleet.avg.health.score", this, FleetAggregator::getFleetAvgHealth)
                .description("Average health score across all vehicles in the fleet")
                .register(registry);
        Gauge.builder("fleet.active.alerts", this, a -> a.getActiveAlerts())
                .description("Number of currently active alerts reported by the diagnostics service")
                .register(registry);
        Gauge.builder("fleet.vehicle.count", this, a -> a.getVehicleCount())
                .description("Number of vehicles currently known to the diagnostics service")
                .register(registry);
    }

    // ------------------------------------------------------------------------
    // Kafka-fed aggregation
    // ------------------------------------------------------------------------

    /**
     * Folds a single diagnostic alert into the in-memory counters. Called by the
     * Kafka listener for every consumed {@link DiagnosticAlert}.
     */
    public void onAlert(DiagnosticAlert alert) {
        if (alert == null) {
            return;
        }
        totalAlerts.increment();

        if (alert.vehicleId() != null) {
            alertsPerVehicle.computeIfAbsent(alert.vehicleId(), k -> new LongAdder()).increment();
        }
        if (alert.type() != null) {
            alertsByType.computeIfAbsent(alert.type(), k -> new LongAdder()).increment();
        }
        // Track the human-readable fault description only for fault-code alerts.
        if (FAULT_CODE_TYPE.equals(alert.type()) && alert.message() != null) {
            topFaults.computeIfAbsent(alert.message(), k -> new LongAdder()).increment();
        }

        log.debug("Aggregated alert vehicle={} type={} severity={}",
                alert.vehicleId(), alert.type(), alert.severity());
    }

    // ------------------------------------------------------------------------
    // Scheduled diagnostics poll
    // ------------------------------------------------------------------------

    /**
     * Periodically pulls per-vehicle health and active-alert counts from the diagnostics
     * service to compute fleet-wide figures. Failures are logged and swallowed so a
     * warming-up or briefly unavailable downstream service never crashes this service.
     */
    @Scheduled(fixedDelay = 10000, initialDelay = 5000)
    public void pollDiagnostics() {
        pollHealth();
        pollActiveAlerts();
    }

    /** Calls {@code /api/diagnostics/health} and recomputes the fleet average + count. */
    private void pollHealth() {
        try {
            Map<String, Double> health = restClient.get()
                    .uri(diagnosticsUrl + "/api/diagnostics/health")
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Double>>() {});

            if (health == null || health.isEmpty()) {
                // Diagnostics service may not have observed any vehicles yet.
                this.fleetAvgHealth = 0.0;
                this.vehicleCount = 0;
                log.debug("Diagnostics health endpoint returned no vehicles");
                return;
            }

            double avg = health.values().stream()
                    .filter(v -> v != null)
                    .mapToDouble(Double::doubleValue)
                    .average()
                    .orElse(0.0);
            this.fleetAvgHealth = avg;
            this.vehicleCount = health.size();
            log.debug("Polled fleet health: vehicles={} avgHealth={}", vehicleCount, fleetAvgHealth);
        } catch (Exception ex) {
            // Service may be warming up or temporarily unreachable; keep the last snapshot.
            log.warn("Failed to poll diagnostics health endpoint at {}: {}", diagnosticsUrl, ex.getMessage());
        }
    }

    /** Calls {@code /api/diagnostics/alerts?activeOnly=true&limit=1000} and records the count. */
    private void pollActiveAlerts() {
        try {
            List<Object> alerts = restClient.get()
                    .uri(diagnosticsUrl + "/api/diagnostics/alerts?activeOnly=true&limit=1000")
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<Object>>() {});

            this.activeAlerts = (alerts == null) ? 0 : alerts.size();
            log.debug("Polled active alerts: {}", activeAlerts);
        } catch (Exception ex) {
            // Service may be warming up or temporarily unreachable; keep the last snapshot.
            log.warn("Failed to poll diagnostics active-alerts endpoint at {}: {}", diagnosticsUrl, ex.getMessage());
        }
    }

    // ------------------------------------------------------------------------
    // Read accessors (used by the REST layer and gauge suppliers)
    // ------------------------------------------------------------------------

    public double getFleetAvgHealth() {
        return fleetAvgHealth;
    }

    public int getVehicleCount() {
        return vehicleCount;
    }

    public int getActiveAlerts() {
        return activeAlerts;
    }

    public long getTotalAlerts() {
        return totalAlerts.sum();
    }

    /** @return an immutable snapshot of alert counts keyed by vehicle id. */
    public Map<String, Long> getAlertsPerVehicle() {
        return snapshot(alertsPerVehicle);
    }

    /** @return an immutable snapshot of alert counts keyed by alert type. */
    public Map<String, Long> getAlertsByType() {
        return snapshot(alertsByType);
    }

    /** @return an immutable snapshot of fault-code occurrence counts keyed by description. */
    public Map<String, Long> getTopFaults() {
        return snapshot(topFaults);
    }

    private static Map<String, Long> snapshot(Map<String, LongAdder> source) {
        Map<String, Long> out = new ConcurrentHashMap<>();
        source.forEach((k, v) -> out.put(k, v.sum()));
        return out;
    }
}
