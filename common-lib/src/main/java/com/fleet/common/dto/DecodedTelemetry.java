package com.fleet.common.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Decoded, human-readable telemetry for one vehicle at one instant, produced by
 * the DBC decoder from one or more raw {@link CanFrame}s.
 *
 * @param vehicleId  logical vehicle / VIN
 * @param canId      source CAN message ID that produced these signals
 * @param messageName DBC message name (e.g. "EngineData")
 * @param signals    signal-name -> engineering value (already scaled/offset),
 *                   e.g. {"EngineSpeed": 2450.0, "CoolantTemp": 92.0}
 * @param faultCodes any DTC / fault codes carried in the frame (may be empty)
 * @param timestamp  capture time carried through from the raw frame
 */
public record DecodedTelemetry(
        String vehicleId,
        long canId,
        String messageName,
        Map<String, Double> signals,
        List<String> faultCodes,
        Instant timestamp
) {
}
