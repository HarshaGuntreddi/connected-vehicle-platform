package com.fleet.storage;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the telemetry-storage-service.
 *
 * <p>Consumes {@link com.fleet.common.dto.DecodedTelemetry} from Kafka, persists
 * each signal as a time-series row in PostgreSQL, and exposes read-only query
 * REST APIs under {@code /api/telemetry}.
 */
@SpringBootApplication
public class TelemetryStorageApplication {
    public static void main(String[] args) {
        SpringApplication.run(TelemetryStorageApplication.class, args);
    }
}
