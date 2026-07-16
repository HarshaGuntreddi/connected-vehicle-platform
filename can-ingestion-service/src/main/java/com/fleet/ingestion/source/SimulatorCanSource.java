package com.fleet.ingestion.source;

import com.fleet.common.dto.CanFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Generates realistic synthetic CAN frames when no CAN hardware is present.
 *
 * <p>Each simulated vehicle keeps evolving physical state (throttle, RPM, speed,
 * coolant temperature, battery voltage). Values are encoded into raw CAN frames
 * using {@link IntelBitCodec} with a bit layout that matches the bundled DBC
 * file, so the dbc-decoder-service reproduces them exactly.
 *
 * <p>To exercise the predictive-diagnostics service, a couple of vehicles run
 * fault scenarios: vehicle 0 gradually overheats, vehicle 1 suffers battery
 * degradation, and occasional random DTC fault codes are emitted.
 */
@Component
@ConditionalOnProperty(name = "can.mode", havingValue = "simulator", matchIfMissing = true)
public class SimulatorCanSource implements CanSource {

    private static final Logger log = LoggerFactory.getLogger(SimulatorCanSource.class);

    // CAN message IDs (see resources DBC in dbc-decoder-service)
    private static final int ID_ENGINE = 0x100;
    private static final int ID_BATTERY = 0x200;
    private static final int ID_FAULT = 0x300;

    // A few example diagnostic trouble codes to emit at random.
    private static final int[] DTC_CODES = {0x0217, 0x0128, 0x0562, 0x0300};

    private final int vehicleCount;
    private final int rateHz;
    private final List<VehicleState> fleet = new ArrayList<>();
    private ScheduledExecutorService scheduler;

    public SimulatorCanSource(@Value("${sim.vehicle-count:3}") int vehicleCount,
                              @Value("${sim.rate-hz:10}") int rateHz) {
        this.vehicleCount = Math.max(1, vehicleCount);
        this.rateHz = Math.max(1, rateHz);
    }

    @Override
    public String name() {
        return "simulator(vehicles=" + vehicleCount + ", rate=" + rateHz + "Hz)";
    }

    @Override
    public void start(Consumer<CanFrame> sink) {
        for (int i = 0; i < vehicleCount; i++) {
            fleet.add(new VehicleState(String.format("VIN-%04d", i + 1), i));
        }
        long periodMs = Math.max(1, 1000 / rateHz);
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "can-simulator");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(() -> tick(sink), 500, periodMs, TimeUnit.MILLISECONDS);
        log.info("CAN simulator started: {} vehicle(s) at {} Hz", vehicleCount, rateHz);
    }

    private void tick(Consumer<CanFrame> sink) {
        try {
            Instant now = Instant.now();
            for (VehicleState v : fleet) {
                v.evolve();
                sink.accept(engineFrame(v, now));
                sink.accept(batteryFrame(v, now));
                if (v.pendingFault != 0) {
                    sink.accept(faultFrame(v, now));
                    v.pendingFault = 0;
                }
            }
        } catch (Exception e) {
            log.error("Simulator tick failed", e);
        }
    }

    private CanFrame engineFrame(VehicleState v, Instant now) {
        long p = 0L;
        p = IntelBitCodec.put(p, 0, 16, IntelBitCodec.rawFrom(v.rpm, 0.25, 0));
        p = IntelBitCodec.put(p, 16, 8, IntelBitCodec.rawFrom(v.coolantTemp, 1, -40));
        p = IntelBitCodec.put(p, 24, 8, IntelBitCodec.rawFrom(v.throttle, 0.4, 0));
        return new CanFrame(v.id, ID_ENGINE, 8, IntelBitCodec.toHex(p, 8), now);
    }

    private CanFrame batteryFrame(VehicleState v, Instant now) {
        long p = 0L;
        p = IntelBitCodec.put(p, 0, 16, IntelBitCodec.rawFrom(v.batteryVoltage, 0.001, 0));
        p = IntelBitCodec.put(p, 16, 16, IntelBitCodec.rawFrom(v.speed, 0.01, 0));
        return new CanFrame(v.id, ID_BATTERY, 8, IntelBitCodec.toHex(p, 8), now);
    }

    private CanFrame faultFrame(VehicleState v, Instant now) {
        long p = IntelBitCodec.put(0L, 0, 16, v.pendingFault);
        return new CanFrame(v.id, ID_FAULT, 8, IntelBitCodec.toHex(p, 8), now);
    }

    @Override
    public void stop() {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }

    /** Per-vehicle evolving physical state. */
    private static final class VehicleState {
        final String id;
        final int index;
        double throttle = 10;     // %
        double rpm = 800;         // idle
        double speed = 0;         // km/h
        double coolantTemp = 85;  // °C
        double batteryVoltage = 13.8; // V
        int pendingFault = 0;
        long ticks = 0;

        VehicleState(String id, int index) {
            this.id = id;
            this.index = index;
        }

        void evolve() {
            ticks++;
            ThreadLocalRandom rnd = ThreadLocalRandom.current();

            // Throttle random walk within [0,100]
            throttle = clamp(throttle + rnd.nextDouble(-8, 8), 0, 100);
            // RPM tracks throttle with idle floor and noise
            double targetRpm = 800 + throttle * 55 + rnd.nextDouble(-100, 100);
            rpm = clamp(rpm + (targetRpm - rpm) * 0.3, 700, 7200);
            // Speed loosely follows RPM
            speed = clamp((rpm - 800) / 55.0 + rnd.nextDouble(-3, 3), 0, 220);

            // Coolant: rises with load, relaxes toward ~90 otherwise
            double load = throttle / 100.0;
            coolantTemp += (0.5 * load) - 0.2 + rnd.nextDouble(-0.3, 0.3);
            coolantTemp = clamp(coolantTemp, 70, 130);
            // Vehicle 0 scenario: gradual overheat after warm-up
            if (index == 0 && ticks > 150) {
                coolantTemp = clamp(coolantTemp + 0.15, 70, 130);
            }

            // Battery ~13.8V running; vehicle 1 scenario: degradation/sag
            batteryVoltage = 13.8 + rnd.nextDouble(-0.15, 0.15);
            if (index == 1 && ticks > 120) {
                batteryVoltage = clamp(12.6 - (ticks - 120) * 0.004 + rnd.nextDouble(-0.1, 0.1), 9.5, 14.5);
            }

            // Occasional random fault code (~1 in 2000 ticks per vehicle)
            if (rnd.nextInt(2000) == 0) {
                pendingFault = DTC_CODES[rnd.nextInt(DTC_CODES.length)];
            }
        }

        static double clamp(double v, double lo, double hi) {
            return Math.max(lo, Math.min(hi, v));
        }
    }
}
