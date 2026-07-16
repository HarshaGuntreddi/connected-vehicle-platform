package com.fleet.common.dto;

import java.time.Instant;

/**
 * A single raw CAN frame as it appears on the bus.
 *
 * <p>On a classic CAN bus a frame carries an 11-bit (or 29-bit extended)
 * arbitration ID plus up to 8 data bytes. We keep the payload as an uppercase
 * hex string ({@code data}) so the frame serialises cleanly to JSON for Kafka.
 *
 * @param vehicleId logical vehicle / VIN this frame came from
 * @param canId     arbitration ID (message identifier) as an unsigned value
 * @param dlc       data length code — number of valid data bytes (0..8)
 * @param data      up to 8 payload bytes encoded as a hex string, e.g. "0AFF1234"
 * @param timestamp capture time
 */
public record CanFrame(
        String vehicleId,
        long canId,
        int dlc,
        String data,
        Instant timestamp
) {
}
