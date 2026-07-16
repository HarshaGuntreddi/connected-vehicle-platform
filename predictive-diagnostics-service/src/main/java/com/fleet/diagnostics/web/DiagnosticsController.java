package com.fleet.diagnostics.web;

import com.fleet.diagnostics.persistence.AlertEntity;
import com.fleet.diagnostics.persistence.AlertRepository;
import com.fleet.diagnostics.service.DiagnosticsEngine;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Read API over live vehicle health scores and persisted diagnostic alerts.
 */
@RestController
@RequestMapping("/api/diagnostics")
@Tag(name = "Diagnostics", description = "Vehicle health scores and diagnostic alerts")
public class DiagnosticsController {

    private final DiagnosticsEngine engine;
    private final AlertRepository repository;

    public DiagnosticsController(DiagnosticsEngine engine, AlertRepository repository) {
        this.engine = engine;
        this.repository = repository;
    }

    @GetMapping("/health")
    @Operation(summary = "Current health score (0-100) for every vehicle seen")
    public Map<String, Double> allHealth() {
        return engine.healthScores();
    }

    @GetMapping("/health/{vehicleId}")
    @Operation(summary = "Health score and active-alert count for one vehicle")
    public ResponseEntity<VehicleHealth> vehicleHealth(@PathVariable String vehicleId) {
        Double score = engine.healthScore(vehicleId);
        if (score == null) {
            return ResponseEntity.notFound().build();
        }
        long active = repository.countByVehicleIdAndResolvedFalse(vehicleId);
        return ResponseEntity.ok(new VehicleHealth(vehicleId, score, active));
    }

    @GetMapping("/alerts")
    @Operation(summary = "List persisted alerts, optionally filtered by vehicle and/or active-only")
    public List<AlertEntity> alerts(@RequestParam(required = false) String vehicleId,
                                    @RequestParam(defaultValue = "false") boolean activeOnly,
                                    @RequestParam(defaultValue = "100") int limit) {
        Pageable page = PageRequest.of(0, Math.max(1, limit));
        if (vehicleId != null && !vehicleId.isBlank()) {
            return activeOnly
                    ? repository.findByVehicleIdAndResolvedFalseOrderByTsDesc(vehicleId, page)
                    : repository.findByVehicleIdOrderByTsDesc(vehicleId, page);
        }
        return activeOnly
                ? repository.findByResolvedFalseOrderByTsDesc(page)
                : repository.findAll(page).getContent();
    }

    /** Response body for GET /health/{vehicleId}. */
    public record VehicleHealth(String vehicleId, double healthScore, long activeAlertCount) {
    }
}
