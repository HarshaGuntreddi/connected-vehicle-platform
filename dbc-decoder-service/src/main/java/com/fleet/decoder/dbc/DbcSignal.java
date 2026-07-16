package com.fleet.decoder.dbc;

/**
 * One signal definition parsed from a DBC {@code SG_} line.
 *
 * @param name        signal name, e.g. "EngineSpeed"
 * @param startBit    LSB position within the payload (Intel bit numbering)
 * @param length      number of bits
 * @param littleEndian true for Intel (@1) byte order, false for Motorola (@0)
 * @param signed      true if the raw value is two's-complement signed
 * @param factor      scale applied to the raw value
 * @param offset      offset added after scaling
 * @param unit        engineering unit (may be empty)
 */
public record DbcSignal(
        String name,
        int startBit,
        int length,
        boolean littleEndian,
        boolean signed,
        double factor,
        double offset,
        String unit
) {
}
