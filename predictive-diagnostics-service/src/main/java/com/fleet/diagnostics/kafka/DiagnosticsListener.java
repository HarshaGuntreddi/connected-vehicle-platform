package com.fleet.diagnostics.kafka;

import com.fleet.common.dto.DecodedTelemetry;
import com.fleet.diagnostics.service.DiagnosticsEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes decoded telemetry and hands each message to the {@link DiagnosticsEngine}
 * for anomaly detection and health scoring.
 */
@Component
public class DiagnosticsListener {

    private static final Logger log = LoggerFactory.getLogger(DiagnosticsListener.class);

    private final DiagnosticsEngine engine;

    public DiagnosticsListener(DiagnosticsEngine engine) {
        this.engine = engine;
    }

    @KafkaListener(topics = "${topic.decoded:decoded-telemetry}", groupId = "predictive-diagnostics")
    public void onTelemetry(DecodedTelemetry telemetry) {
        try {
            engine.process(telemetry);
        } catch (Exception ex) {
            // Never let a single bad message stall the consumer; log and move on.
            log.error("Failed to process telemetry for vehicle={}",
                    telemetry != null ? telemetry.vehicleId() : null, ex);
        }
    }
}
