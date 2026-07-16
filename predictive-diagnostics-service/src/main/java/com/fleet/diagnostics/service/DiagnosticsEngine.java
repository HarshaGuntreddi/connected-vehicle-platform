package com.fleet.diagnostics.service;

import com.fleet.common.dto.DecodedTelemetry;
import com.fleet.common.dto.DiagnosticAlert;
import com.fleet.common.dto.DiagnosticAlert.Severity;
import com.fleet.diagnostics.config.DiagnosticsProperties;
import com.fleet.diagnostics.persistence.AlertEntity;
import com.fleet.diagnostics.persistence.AlertRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Core anomaly-detection engine.
 *
 * <p>For every {@link DecodedTelemetry} message it:
 * <ol>
 *   <li>updates per-vehicle / per-signal rolling windows (last {@value #WINDOW_SIZE} values)
 *       used for z-score computation;</li>
 *   <li>evaluates rule-based threshold checks (coolant, battery, rpm) and a rolling
 *       z-score check on EngineSpeed, plus any fault codes carried in the frame;</li>
 *   <li>applies a per-(vehicle,type) dedupe/cooldown so a persistently-tripped condition
 *       does not flood the alert stream at 10&nbsp;Hz;</li>
 *   <li>for each surviving alert: publishes to the alerts topic (keyed by vehicleId),
 *       persists it, and increments the {@code diagnostic.alerts} counter;</li>
 *   <li>recomputes and publishes the vehicle's 0-100 health score.</li>
 * </ol>
 *
 * <p>All mutable state is keyed by vehicle and guarded by synchronising
 * {@link #process(DecodedTelemetry)}, which is sufficient because the Kafka
 * listener drives it and per-message work is cheap.
 */
@Service
public class DiagnosticsEngine {

    private static final Logger log = LoggerFactory.getLogger(DiagnosticsEngine.class);

    /** Rolling window depth for z-score statistics. */
    private static final int WINDOW_SIZE = 50;
    /** Minimum samples before a z-score is considered meaningful. */
    private static final int MIN_ZSCORE_SAMPLES = 20;
    /** Cooldown: re-emit an unchanged alert only after this elapses. */
    private static final Duration COOLDOWN = Duration.ofSeconds(60);

    private static final String SIG_COOLANT = "CoolantTemp";
    private static final String SIG_BATTERY = "BatteryVoltage";
    private static final String SIG_RPM = "EngineSpeed";

    private final KafkaTemplate<String, DiagnosticAlert> kafkaTemplate;
    private final AlertRepository repository;
    private final MeterRegistry registry;
    private final DiagnosticsProperties props;
    private final String alertsTopic;

    /** How long a fault code counts against health after it was last seen. */
    private static final Duration FAULT_TTL = Duration.ofSeconds(60);

    /** vehicle -> signal -> rolling window of recent values. */
    private final Map<String, Map<String, Deque<Double>>> windows = new ConcurrentHashMap<>();
    /** vehicle -> latest known value per signal (merged across message types). */
    private final Map<String, Map<String, Double>> latestSignals = new ConcurrentHashMap<>();
    /** vehicle -> fault code message -> last time it was observed. */
    private final Map<String, Map<String, Instant>> activeFaults = new ConcurrentHashMap<>();
    /** vehicle -> latest health score (0-100). */
    private final Map<String, Double> healthScores = new ConcurrentHashMap<>();
    /** vehicle -> gauge backing reference (kept so the gauge is registered exactly once). */
    private final Map<String, AtomicReference<Double>> healthGauges = new ConcurrentHashMap<>();
    /** dedupe key -> last emission state for cooldown / escalation decisions. */
    private final Map<String, EmitState> lastEmitted = new ConcurrentHashMap<>();

    public DiagnosticsEngine(KafkaTemplate<String, DiagnosticAlert> kafkaTemplate,
                             AlertRepository repository,
                             MeterRegistry registry,
                             DiagnosticsProperties props,
                             @Value("${topic.alerts:diagnostic-alerts}") String alertsTopic) {
        this.kafkaTemplate = kafkaTemplate;
        this.repository = repository;
        this.registry = registry;
        this.props = props;
        this.alertsTopic = alertsTopic;
    }

    /** Latest health score per vehicle (defensive copy). */
    public Map<String, Double> healthScores() {
        return new HashMap<>(healthScores);
    }

    /** Latest health score for one vehicle, or null if the vehicle has not been seen. */
    public Double healthScore(String vehicleId) {
        return healthScores.get(vehicleId);
    }

    /**
     * Evaluate one telemetry message: detect anomalies, emit/persist alerts, and
     * refresh the vehicle health score.
     */
    public synchronized void process(DecodedTelemetry t) {
        if (t == null || t.vehicleId() == null) {
            return;
        }
        String vehicleId = t.vehicleId();
        Map<String, Double> signals = t.signals() != null ? t.signals() : Map.of();
        Instant ts = t.timestamp() != null ? t.timestamp() : Instant.now();

        // Feed rolling windows and merge the latest values so both z-score stats
        // and the health score reflect the vehicle's full state across message types
        // (EngineData and BatteryData arrive as separate frames).
        Map<String, Double> latest = latestSignals.computeIfAbsent(vehicleId, v -> new ConcurrentHashMap<>());
        for (Map.Entry<String, Double> e : signals.entrySet()) {
            if (e.getValue() != null) {
                pushWindow(vehicleId, e.getKey(), e.getValue());
                latest.put(e.getKey(), e.getValue());
            }
        }

        List<DiagnosticAlert> candidates = new ArrayList<>();

        // --- Rule 1: coolant temperature overheat -----------------------------
        Double coolant = signals.get(SIG_COOLANT);
        if (coolant != null) {
            if (coolant > props.getCoolantTempCrit()) {
                candidates.add(alert(vehicleId, "OVERHEAT", Severity.CRITICAL, SIG_COOLANT, coolant,
                        "Coolant temperature critical: " + coolant + " > " + props.getCoolantTempCrit(), ts));
            } else if (coolant > props.getCoolantTempWarn()) {
                candidates.add(alert(vehicleId, "OVERHEAT", Severity.WARNING, SIG_COOLANT, coolant,
                        "Coolant temperature high: " + coolant + " > " + props.getCoolantTempWarn(), ts));
            }
        }

        // --- Rule 2: low battery voltage --------------------------------------
        Double battery = signals.get(SIG_BATTERY);
        if (battery != null && battery < props.getBatteryVoltageMin()) {
            // More than 1.0 V under the floor is treated as critical.
            Severity sev = battery < props.getBatteryVoltageMin() - 1.0 ? Severity.CRITICAL : Severity.WARNING;
            candidates.add(alert(vehicleId, "BATTERY_LOW", sev, SIG_BATTERY, battery,
                    "Battery voltage low: " + battery + " < " + props.getBatteryVoltageMin(), ts));
        }

        // --- Rule 3: engine speed threshold + rolling z-score anomaly ---------
        Double rpm = signals.get(SIG_RPM);
        if (rpm != null) {
            if (rpm > props.getRpmMax()) {
                candidates.add(alert(vehicleId, "RPM_HIGH", Severity.WARNING, SIG_RPM, rpm,
                        "Engine speed over limit: " + rpm + " > " + props.getRpmMax(), ts));
            }
            // z = (x - mean) / stddev over the rolling window; needs enough samples.
            Deque<Double> window = window(vehicleId, SIG_RPM);
            if (window.size() >= MIN_ZSCORE_SAMPLES) {
                double z = zScore(window, rpm);
                if (Math.abs(z) > props.getZscoreThreshold()) {
                    candidates.add(alert(vehicleId, "RPM_ANOMALY", Severity.WARNING, SIG_RPM, rpm,
                            "Engine speed anomaly (z=" + round(z) + ") value=" + rpm, ts));
                }
            }
        }

        // --- Rule 4: fault codes (DTCs) ---------------------------------------
        List<String> faultCodes = t.faultCodes();
        if (faultCodes != null) {
            Map<String, Instant> faults = activeFaults.computeIfAbsent(vehicleId, v -> new ConcurrentHashMap<>());
            for (String code : faultCodes) {
                if (code == null || code.isBlank()) {
                    continue;
                }
                faults.put(code, ts);
                candidates.add(alert(vehicleId, "FAULT_CODE", Severity.CRITICAL, "FaultCode", 0.0, code, ts));
            }
        }

        // Emit surviving alerts (after dedupe/cooldown).
        for (DiagnosticAlert candidate : candidates) {
            if (shouldEmit(candidate)) {
                emit(candidate);
            }
        }

        // Recompute health from the vehicle's merged latest state (stable across
        // message types), not just the signals in this single frame.
        updateHealth(vehicleId, computeHealth(vehicleId, ts));
    }

    /**
     * Health score in [0,100] derived from the vehicle's latest known signal
     * values plus any fault codes seen within {@link #FAULT_TTL}. Because it uses
     * merged state rather than a single frame, the score stays stable as the
     * different CAN message types stream in.
     */
    private double computeHealth(String vehicleId, Instant now) {
        Map<String, Double> latest = latestSignals.getOrDefault(vehicleId, Map.of());
        double penalty = 0.0;

        Double coolant = latest.get(SIG_COOLANT);
        if (coolant != null) {
            if (coolant > props.getCoolantTempCrit()) {
                penalty += 60;
            } else if (coolant > props.getCoolantTempWarn()) {
                penalty += 30;
            }
        }
        Double battery = latest.get(SIG_BATTERY);
        if (battery != null && battery < props.getBatteryVoltageMin()) {
            penalty += (battery < props.getBatteryVoltageMin() - 1.0) ? 40 : 25;
        }
        Double rpm = latest.get(SIG_RPM);
        if (rpm != null && rpm > props.getRpmMax()) {
            penalty += 15;
        }

        // Count fault codes still within their TTL; purge the expired ones.
        Map<String, Instant> faults = activeFaults.get(vehicleId);
        if (faults != null) {
            faults.entrySet().removeIf(e -> Duration.between(e.getValue(), now).compareTo(FAULT_TTL) > 0);
            penalty += 20L * faults.size();
        }

        return clamp(100.0 - penalty);
    }

    // ------------------------------------------------------------------ helpers

    private DiagnosticAlert alert(String vehicleId, String type, Severity severity,
                                  String signal, double value, String message, Instant ts) {
        return new DiagnosticAlert(vehicleId, type, severity, signal, value, message, ts);
    }

    /**
     * Dedupe / cooldown gate. Emit only when the condition is newly triggered,
     * the severity has escalated, or the cooldown window has elapsed.
     */
    private boolean shouldEmit(DiagnosticAlert a) {
        String key = dedupeKey(a);
        EmitState prev = lastEmitted.get(key);
        Instant now = a.timestamp();
        if (prev == null) {
            return true; // newly triggered
        }
        if (a.severity().ordinal() > prev.severity.ordinal()) {
            return true; // escalation (e.g. WARNING -> CRITICAL)
        }
        return Duration.between(prev.at, now).compareTo(COOLDOWN) >= 0;
    }

    private void emit(DiagnosticAlert a) {
        // Publish to Kafka keyed by vehicleId (keeps a vehicle's alerts ordered).
        kafkaTemplate.send(alertsTopic, a.vehicleId(), a);
        // Persist as an unresolved alert.
        repository.save(AlertEntity.fromAlert(a));
        // Prometheus: diagnostic_alerts_total{severity="..."}.
        registry.counter("diagnostic.alerts", "severity", a.severity().name()).increment();
        // Record the emission for future dedupe decisions.
        lastEmitted.put(dedupeKey(a), new EmitState(a.timestamp(), a.severity()));
        log.info("Alert raised vehicle={} type={} severity={} signal={} value={} msg='{}'",
                a.vehicleId(), a.type(), a.severity(), a.signal(), a.value(), a.message());
    }

    /**
     * Dedupe key is (vehicle,type); for fault codes the code itself is folded in so
     * distinct DTCs are reported separately while a repeated DTC is still throttled.
     */
    private String dedupeKey(DiagnosticAlert a) {
        if ("FAULT_CODE".equals(a.type())) {
            return a.vehicleId() + "|" + a.type() + "|" + a.message();
        }
        return a.vehicleId() + "|" + a.type();
    }

    private void updateHealth(String vehicleId, double score) {
        healthScores.put(vehicleId, score);
        // Register the gauge exactly once per vehicle; subsequent updates mutate the ref.
        AtomicReference<Double> ref = healthGauges.computeIfAbsent(vehicleId, v -> {
            AtomicReference<Double> r = new AtomicReference<>(score);
            Gauge.builder("vehicle.health.score", r, AtomicReference::get)
                    .description("Per-vehicle health score (0-100)")
                    .tag("vehicle", v)
                    .register(registry);
            return r;
        });
        ref.set(score);
    }

    private void pushWindow(String vehicleId, String signal, double value) {
        Deque<Double> dq = window(vehicleId, signal);
        dq.addLast(value);
        while (dq.size() > WINDOW_SIZE) {
            dq.removeFirst();
        }
    }

    private Deque<Double> window(String vehicleId, String signal) {
        return windows
                .computeIfAbsent(vehicleId, v -> new ConcurrentHashMap<>())
                .computeIfAbsent(signal, s -> new ArrayDeque<>());
    }

    /** Population z-score of {@code value} relative to the window's mean/stddev. */
    private static double zScore(Deque<Double> window, double value) {
        int n = window.size();
        double sum = 0.0;
        for (double d : window) {
            sum += d;
        }
        double mean = sum / n;
        double sq = 0.0;
        for (double d : window) {
            double diff = d - mean;
            sq += diff * diff;
        }
        double std = Math.sqrt(sq / n);
        if (std == 0.0) {
            return 0.0; // no variance -> no anomaly
        }
        return (value - mean) / std;
    }

    private static double clamp(double v) {
        return Math.max(0.0, Math.min(100.0, v));
    }

    private static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    /** Last-emission bookkeeping for the dedupe/cooldown gate. */
    private static final class EmitState {
        private final Instant at;
        private final Severity severity;

        private EmitState(Instant at, Severity severity) {
            this.at = at;
            this.severity = severity;
        }
    }
}
