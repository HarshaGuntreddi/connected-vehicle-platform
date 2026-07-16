package com.fleet.storage.kafka;

import com.fleet.common.dto.DecodedTelemetry;
import com.fleet.storage.domain.TelemetrySample;
import com.fleet.storage.repo.TelemetryRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Consumes {@link DecodedTelemetry} and persists each contained signal as an
 * individual {@link TelemetrySample} time-series row. Maintains a per-row
 * counter and per-(vehicle,signal) gauges for a whitelist of signals.
 */
@Component
public class StorageListener {

    private static final Logger log = LoggerFactory.getLogger(StorageListener.class);

    /** Signals for which a live {@code vehicle_signal_value} gauge is maintained. */
    private static final Set<String> GAUGE_SIGNALS =
            Set.of("CoolantTemp", "EngineSpeed", "BatteryVoltage", "VehicleSpeed");

    private final TelemetryRepository repository;
    private final MeterRegistry registry;
    private final Counter storedCounter;

    /** Latest gauge value holders keyed by "vehicle|signal". */
    private final Map<String, AtomicReference<Double>> gaugeValues = new ConcurrentHashMap<>();

    public StorageListener(TelemetryRepository repository, MeterRegistry registry) {
        this.repository = repository;
        this.registry = registry;
        this.storedCounter = Counter.builder("telemetry.stored")
                .description("Total telemetry rows persisted").register(registry);
    }

    @KafkaListener(topics = "${topic.decoded:decoded-telemetry}", groupId = "telemetry-storage")
    public void onTelemetry(DecodedTelemetry telemetry) {
        Map<String, Double> signals = telemetry.signals();
        if (signals == null || signals.isEmpty()) {
            return;
        }

        List<TelemetrySample> batch = new ArrayList<>(signals.size());
        for (Map.Entry<String, Double> entry : signals.entrySet()) {
            String signalName = entry.getKey();
            double value = entry.getValue();
            batch.add(new TelemetrySample(
                    telemetry.vehicleId(),
                    telemetry.messageName(),
                    signalName,
                    value,
                    telemetry.timestamp()));
            updateGauge(telemetry.vehicleId(), signalName, value);
        }

        repository.saveAll(batch);
        storedCounter.increment(batch.size());
        log.debug("Stored {} telemetry rows for vehicle {}", batch.size(), telemetry.vehicleId());
    }

    /**
     * Update (and lazily register) the {@code vehicle.signal.value} gauge for a
     * whitelisted signal.
     */
    private void updateGauge(String vehicle, String signal, double value) {
        if (!GAUGE_SIGNALS.contains(signal)) {
            return;
        }
        String key = vehicle + "|" + signal;
        AtomicReference<Double> ref = gaugeValues.computeIfAbsent(key, k -> {
            AtomicReference<Double> created = new AtomicReference<>(value);
            Gauge.builder("vehicle.signal.value", created, AtomicReference::get)
                    .tag("vehicle", vehicle)
                    .tag("signal", signal)
                    .register(registry);
            return created;
        });
        ref.set(value);
    }
}
