package com.fleet.diagnostics;

import com.fleet.diagnostics.config.DiagnosticsProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Predictive diagnostics microservice.
 *
 * <p>Consumes {@code decoded-telemetry}, applies rule-based threshold checks and
 * rolling z-score anomaly detection, publishes {@link com.fleet.common.dto.DiagnosticAlert}s
 * to {@code diagnostic-alerts}, persists them to Postgres, and maintains a live
 * per-vehicle health score exposed over REST and Prometheus.
 */
@SpringBootApplication
@EnableConfigurationProperties(DiagnosticsProperties.class)
public class PredictiveDiagnosticsApplication {
    public static void main(String[] args) {
        SpringApplication.run(PredictiveDiagnosticsApplication.class, args);
    }
}
