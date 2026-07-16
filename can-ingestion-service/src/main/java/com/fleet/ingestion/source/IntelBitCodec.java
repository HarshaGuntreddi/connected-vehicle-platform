package com.fleet.ingestion.source;

/**
 * Minimal Intel (little-endian) CAN signal bit-packing helper.
 *
 * <p>CAN payloads are up to 8 bytes. In "Intel" byte order the payload is
 * interpreted as a little-endian 64-bit integer (byte 0 = least significant),
 * and a signal occupies {@code length} bits starting at {@code startBit}.
 * Encoding a raw value therefore reduces to shifting it into place; decoding
 * (see the dbc-decoder-service) is the inverse shift. Keeping both sides on
 * this identical convention guarantees the decoder reproduces the simulator's
 * values exactly.
 */
public final class IntelBitCodec {

    private IntelBitCodec() {
    }

    /** Place a raw integer signal value into a 64-bit little-endian payload. */
    public static long put(long payload, int startBit, int length, long rawValue) {
        long mask = (length == 64) ? -1L : ((1L << length) - 1);
        return payload | ((rawValue & mask) << startBit);
    }

    /** Convert the lowest {@code dlc} bytes of the payload to an uppercase hex string. */
    public static String toHex(long payload, int dlc) {
        StringBuilder sb = new StringBuilder(dlc * 2);
        for (int i = 0; i < dlc; i++) {
            int b = (int) ((payload >>> (i * 8)) & 0xFF);
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }

    /** Convert a physical engineering value to its raw integer using factor/offset. */
    public static long rawFrom(double physical, double factor, double offset) {
        return Math.round((physical - offset) / factor);
    }
}
