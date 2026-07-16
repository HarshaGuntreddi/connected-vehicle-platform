package com.fleet.ingestion.web;

import com.fleet.ingestion.IngestionRunner;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Exposes ingestion status. The actual frame production runs in the background
 * (simulator scheduler or hardware reader thread); this is a read-only view.
 */
@RestController
@RequestMapping("/api/ingestion")
@Tag(name = "CAN Ingestion", description = "Status of the CAN frame ingestion pipeline")
public class IngestionController {

    private final IngestionRunner runner;

    public IngestionController(IngestionRunner runner) {
        this.runner = runner;
    }

    @GetMapping("/status")
    @Operation(summary = "Current ingestion source and counters")
    public Map<String, Object> status() {
        return Map.of(
                "source", runner.sourceName(),
                "framesSeen", runner.framesSeen(),
                "lastFrameAt", String.valueOf(runner.lastFrameAt())
        );
    }
}
