package com.fleet.storage.web;

import com.fleet.storage.domain.TelemetrySample;

import java.time.Instant;

/**
 * REST representation of a single stored telemetry sample.
 */
public record SampleResponse(String vehicleId, String signal, double value, String messageName, Instant ts) {

    public static SampleResponse from(TelemetrySample s) {
        return new SampleResponse(s.getVehicleId(), s.getSignal(), s.getValue(), s.getMessageName(), s.getTs());
    }
}
