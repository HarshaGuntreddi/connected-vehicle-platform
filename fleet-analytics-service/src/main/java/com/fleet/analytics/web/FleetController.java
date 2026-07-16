package com.fleet.analytics.web;

import com.fleet.analytics.service.FleetAggregator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Read-only REST API exposing the aggregated fleet metrics computed by
 * {@link FleetAggregator}.
 */
@RestController
@RequestMapping("/api/fleet")
@Tag(name = "Fleet Analytics", description = "Fleet-wide aggregated diagnostics and health metrics")
public class FleetController {

    private final FleetAggregator aggregator;

    public FleetController(FleetAggregator aggregator) {
        this.aggregator = aggregator;
    }

    /** A single top-fault entry: the fault description and how many times it occurred. */
    public record FaultCount(String fault, long count) {
    }

    /** Aggregated fleet summary returned by {@code GET /api/fleet/summary}. */
    public record FleetSummary(
            double fleetAvgHealth,
            int vehicleCount,
            int activeAlerts,
            long totalAlerts,
            Map<String, Long> alertsPerVehicle,
            Map<String, Long> alertsByType) {
    }

    @Operation(summary = "Fleet summary",
            description = "Fleet average health, vehicle count, active alerts and per-vehicle / per-type alert counts.")
    @GetMapping("/summary")
    public FleetSummary summary() {
        return new FleetSummary(
                aggregator.getFleetAvgHealth(),
                aggregator.getVehicleCount(),
                aggregator.getActiveAlerts(),
                aggregator.getTotalAlerts(),
                aggregator.getAlertsPerVehicle(),
                aggregator.getAlertsByType());
    }

    @Operation(summary = "Top fault codes",
            description = "Fault-code descriptions ranked by number of occurrences, most frequent first.")
    @GetMapping("/top-faults")
    public List<FaultCount> topFaults(
            @Parameter(description = "Maximum number of faults to return")
            @RequestParam(name = "limit", defaultValue = "10") int limit) {
        return aggregator.getTopFaults().entrySet().stream()
                .map(e -> new FaultCount(e.getKey(), e.getValue()))
                .sorted(Comparator.comparingLong(FaultCount::count).reversed())
                .limit(Math.max(0, limit))
                .toList();
    }

    @Operation(summary = "Alerts per vehicle",
            description = "Total alert count keyed by vehicle id.")
    @GetMapping("/alerts-per-vehicle")
    public Map<String, Long> alertsPerVehicle() {
        return aggregator.getAlertsPerVehicle();
    }
}
