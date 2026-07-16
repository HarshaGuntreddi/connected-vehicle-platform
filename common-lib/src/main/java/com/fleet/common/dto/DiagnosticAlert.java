package com.fleet.common.dto;

import java.time.Instant;

/**
 * A diagnostic alert raised by the predictive-diagnostics service.
 *
 * @param vehicleId logical vehicle / VIN the alert concerns
 * @param type      machine-readable alert type, e.g. "OVERHEAT", "BATTERY_LOW",
 *                  "RPM_ANOMALY", "FAULT_CODE"
 * @param severity  severity level
 * @param signal    the signal that triggered the alert (e.g. "CoolantTemp")
 * @param value     the observed value that triggered the alert
 * @param message   human-readable description
 * @param timestamp when the alert was raised
 */
public record DiagnosticAlert(
        String vehicleId,
        String type,
        Severity severity,
        String signal,
        double value,
        String message,
        Instant timestamp
) {
    public enum Severity {
        INFO, WARNING, CRITICAL
    }
}
