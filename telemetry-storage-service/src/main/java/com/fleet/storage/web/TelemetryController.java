package com.fleet.storage.web;

import com.fleet.storage.service.TelemetryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Read-only query API over stored telemetry.
 */
@RestController
@RequestMapping("/api/telemetry")
@Tag(name = "Telemetry Query", description = "Query stored time-series telemetry")
public class TelemetryController {

    private static final Logger log = LoggerFactory.getLogger(TelemetryController.class);

    private final TelemetryService service;

    public TelemetryController(TelemetryService service) {
        this.service = service;
    }

    /**
     * Query samples for a vehicle+signal within an optional time window. Defaults
     * to the last hour and at most 500 rows.
     */
    @GetMapping
    @Operation(summary = "Query telemetry samples for a vehicle and signal within a time window")
    public List<SampleResponse> query(
            @RequestParam String vehicleId,
            @RequestParam String signal,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false, defaultValue = "500") int limit) {

        Instant now = Instant.now();
        Instant toResolved = to != null ? to : now;
        Instant fromResolved = from != null ? from : now.minus(1, ChronoUnit.HOURS);
        int limitResolved = limit > 0 ? limit : 500;

        return service.querySamples(vehicleId, signal, fromResolved, toResolved, limitResolved)
                .stream()
                .map(SampleResponse::from)
                .toList();
    }

    /**
     * Latest value per signal for a vehicle.
     */
    @GetMapping("/{vehicleId}/latest")
    @Operation(summary = "Latest value and timestamp per signal for a vehicle")
    public Map<String, TelemetryService.LatestValue> latest(@PathVariable String vehicleId) {
        return new LinkedHashMap<>(service.latestPerSignal(vehicleId));
    }

    /**
     * Distinct vehicle ids known to the store.
     */
    @GetMapping("/vehicles")
    @Operation(summary = "List distinct vehicle ids with stored telemetry")
    public List<String> vehicles() {
        return service.vehicles();
    }
}
