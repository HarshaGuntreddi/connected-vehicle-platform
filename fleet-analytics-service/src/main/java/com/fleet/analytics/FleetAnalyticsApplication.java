package com.fleet.analytics;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point for the fleet-analytics-service.
 *
 * <p>Aggregates fleet-wide metrics by consuming {@code diagnostic-alerts} from Kafka
 * and periodically polling the diagnostics service for per-vehicle health scores.
 * Scheduling is enabled so the periodic health poll can run.
 */
@SpringBootApplication
@EnableScheduling
public class FleetAnalyticsApplication {

    public static void main(String[] args) {
        SpringApplication.run(FleetAnalyticsApplication.class, args);
    }
}
