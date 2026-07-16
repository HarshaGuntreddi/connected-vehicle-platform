package com.fleet.common;

/**
 * Central registry of Kafka topic names shared across services.
 * Values mirror the TOPIC_* environment variables in .env; services override
 * them via configuration, but these serve as documentation and safe defaults.
 */
public final class Topics {

    private Topics() {
    }

    /** Raw, undecoded CAN frames straight off the bus / simulator. */
    public static final String RAW_CAN_FRAMES = "raw-can-frames";

    /** Human-readable signals after DBC decoding. */
    public static final String DECODED_TELEMETRY = "decoded-telemetry";

    /** Diagnostic alerts emitted by the predictive-diagnostics service. */
    public static final String DIAGNOSTIC_ALERTS = "diagnostic-alerts";
}
