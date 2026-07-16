package com.fleet.diagnostics.persistence;

import com.fleet.common.dto.DiagnosticAlert;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * JPA mapping of a persisted diagnostic alert. Maps to the pre-created
 * {@code diagnostic_alerts} table (schema managed outside the app; ddl-auto=none).
 */
@Entity
@Table(name = "diagnostic_alerts")
public class AlertEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // BIGSERIAL
    private Long id;

    @Column(name = "vehicle_id")
    private String vehicleId;

    @Column(name = "type")
    private String type;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity")
    private DiagnosticAlert.Severity severity;

    @Column(name = "signal")
    private String signal;

    @Column(name = "value")
    private double value;

    @Column(name = "message", columnDefinition = "text")
    private String message;

    @Column(name = "ts")
    private Instant ts;

    @Column(name = "resolved")
    private boolean resolved;

    protected AlertEntity() {
        // for JPA
    }

    public AlertEntity(String vehicleId, String type, DiagnosticAlert.Severity severity,
                       String signal, double value, String message, Instant ts, boolean resolved) {
        this.vehicleId = vehicleId;
        this.type = type;
        this.severity = severity;
        this.signal = signal;
        this.value = value;
        this.message = message;
        this.ts = ts;
        this.resolved = resolved;
    }

    /** Build an unresolved entity from a freshly-raised alert. */
    public static AlertEntity fromAlert(DiagnosticAlert alert) {
        return new AlertEntity(
                alert.vehicleId(),
                alert.type(),
                alert.severity(),
                alert.signal(),
                alert.value(),
                alert.message(),
                alert.timestamp(),
                false);
    }

    public Long getId() {
        return id;
    }

    public String getVehicleId() {
        return vehicleId;
    }

    public String getType() {
        return type;
    }

    public DiagnosticAlert.Severity getSeverity() {
        return severity;
    }

    public String getSignal() {
        return signal;
    }

    public double getValue() {
        return value;
    }

    public String getMessage() {
        return message;
    }

    public Instant getTs() {
        return ts;
    }

    public boolean isResolved() {
        return resolved;
    }

    public void setResolved(boolean resolved) {
        this.resolved = resolved;
    }
}
