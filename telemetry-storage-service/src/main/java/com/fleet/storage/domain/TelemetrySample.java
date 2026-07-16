package com.fleet.storage.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * One persisted time-series sample: a single decoded signal value for a vehicle
 * at a point in time. Maps to the pre-created {@code telemetry} table (schema is
 * managed by init SQL, not Hibernate).
 */
@Entity
@Table(name = "telemetry")
public class TelemetrySample {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "vehicle_id")
    private String vehicleId;

    @Column(name = "message_name")
    private String messageName;

    @Column(name = "signal")
    private String signal;

    @Column(name = "value")
    private double value;

    @Column(name = "ts")
    private Instant ts;

    /** Required no-args constructor for JPA. */
    protected TelemetrySample() {
    }

    public TelemetrySample(String vehicleId, String messageName, String signal, double value, Instant ts) {
        this.vehicleId = vehicleId;
        this.messageName = messageName;
        this.signal = signal;
        this.value = value;
        this.ts = ts;
    }

    public Long getId() {
        return id;
    }

    public String getVehicleId() {
        return vehicleId;
    }

    public String getMessageName() {
        return messageName;
    }

    public String getSignal() {
        return signal;
    }

    public double getValue() {
        return value;
    }

    public Instant getTs() {
        return ts;
    }
}
