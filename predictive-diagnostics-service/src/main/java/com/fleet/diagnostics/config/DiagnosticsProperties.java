package com.fleet.diagnostics.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Externalised anomaly-detection thresholds, bound from the {@code diagnostics.*}
 * configuration keys (see application.yml). All values are overridable via env.
 */
@ConfigurationProperties(prefix = "diagnostics")
public class DiagnosticsProperties {

    /** CoolantTemp above this (deg C) raises a WARNING overheat alert. */
    private double coolantTempWarn = 105.0;

    /** CoolantTemp above this (deg C) raises a CRITICAL overheat alert. */
    private double coolantTempCrit = 115.0;

    /** BatteryVoltage below this (V) raises a battery-low alert. */
    private double batteryVoltageMin = 11.8;

    /** EngineSpeed above this (rpm) raises a high-rpm alert. */
    private double rpmMax = 6500.0;

    /** Absolute rolling z-score above this flags a statistical anomaly. */
    private double zscoreThreshold = 3.0;

    public double getCoolantTempWarn() {
        return coolantTempWarn;
    }

    public void setCoolantTempWarn(double coolantTempWarn) {
        this.coolantTempWarn = coolantTempWarn;
    }

    public double getCoolantTempCrit() {
        return coolantTempCrit;
    }

    public void setCoolantTempCrit(double coolantTempCrit) {
        this.coolantTempCrit = coolantTempCrit;
    }

    public double getBatteryVoltageMin() {
        return batteryVoltageMin;
    }

    public void setBatteryVoltageMin(double batteryVoltageMin) {
        this.batteryVoltageMin = batteryVoltageMin;
    }

    public double getRpmMax() {
        return rpmMax;
    }

    public void setRpmMax(double rpmMax) {
        this.rpmMax = rpmMax;
    }

    public double getZscoreThreshold() {
        return zscoreThreshold;
    }

    public void setZscoreThreshold(double zscoreThreshold) {
        this.zscoreThreshold = zscoreThreshold;
    }
}
