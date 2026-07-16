package com.fleet.analytics.kafka;

import com.fleet.common.dto.DiagnosticAlert;
import com.fleet.analytics.service.FleetAggregator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes {@link DiagnosticAlert} events from the {@code diagnostic-alerts} topic and
 * folds each one into the {@link FleetAggregator}'s in-memory counters.
 */
@Component
public class AlertListener {

    private static final Logger log = LoggerFactory.getLogger(AlertListener.class);

    private final FleetAggregator aggregator;

    public AlertListener(FleetAggregator aggregator) {
        this.aggregator = aggregator;
    }

    @KafkaListener(topics = "${topic.alerts:diagnostic-alerts}", groupId = "fleet-analytics")
    public void onAlert(DiagnosticAlert alert) {
        log.debug("Received diagnostic alert: {}", alert);
        aggregator.onAlert(alert);
    }
}
