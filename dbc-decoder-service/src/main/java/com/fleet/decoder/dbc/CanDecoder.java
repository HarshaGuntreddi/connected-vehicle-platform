package com.fleet.decoder.dbc;

import com.fleet.common.dto.CanFrame;
import com.fleet.common.dto.DecodedTelemetry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Decodes raw {@link CanFrame}s into {@link DecodedTelemetry} using a
 * {@link DbcFile}. Uses Intel (little-endian) bit extraction — the inverse of
 * the packing done by the ingestion simulator's {@code IntelBitCodec}.
 *
 * <p>The payload's lowest {@code dlc} bytes are loaded into a 64-bit integer
 * (byte 0 = least significant); a signal's raw value is then
 * {@code (payload >>> startBit) & mask}, sign-extended if needed, and scaled by
 * {@code factor}/{@code offset}. Motorola (big-endian) signals are not present
 * in the bundled DBC and are skipped with a warning if encountered.
 */
public class CanDecoder {

    private static final Logger log = LoggerFactory.getLogger(CanDecoder.class);

    private final DbcFile dbc;

    public CanDecoder(DbcFile dbc) {
        this.dbc = dbc;
    }

    public Optional<DecodedTelemetry> decode(CanFrame frame) {
        Optional<DbcMessage> maybeMsg = dbc.message(frame.canId());
        if (maybeMsg.isEmpty()) {
            return Optional.empty(); // unknown message ID — ignore
        }
        DbcMessage msg = maybeMsg.get();
        long payload = loadLittleEndian(frame.data());

        Map<String, Double> signals = new LinkedHashMap<>();
        List<String> faultCodes = new ArrayList<>();

        for (DbcSignal sig : msg.signals()) {
            if (!sig.littleEndian()) {
                log.warn("Skipping Motorola signal {} — only Intel byte order is supported", sig.name());
                continue;
            }
            long raw = extract(payload, sig.startBit(), sig.length());
            if (sig.signed()) {
                raw = signExtend(raw, sig.length());
            }
            double value = raw * sig.factor() + sig.offset();
            signals.put(sig.name(), value);

            // Map non-zero fault codes to DTC strings
            if ("FaultCode".equals(sig.name()) && raw != 0) {
                faultCodes.add(DtcCatalog.describe((int) raw));
            }
        }

        return Optional.of(new DecodedTelemetry(
                frame.vehicleId(), frame.canId(), msg.name(),
                signals, faultCodes,
                frame.timestamp() != null ? frame.timestamp() : Instant.now()));
    }

    /** Load up to 8 payload bytes little-endian (byte 0 -> least significant). */
    private static long loadLittleEndian(String hex) {
        long payload = 0L;
        int bytes = hex.length() / 2;
        for (int i = 0; i < bytes && i < 8; i++) {
            int b = Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
            payload |= ((long) (b & 0xFF)) << (i * 8);
        }
        return payload;
    }

    private static long extract(long payload, int startBit, int length) {
        long mask = (length == 64) ? -1L : ((1L << length) - 1);
        return (payload >>> startBit) & mask;
    }

    private static long signExtend(long raw, int length) {
        long signBit = 1L << (length - 1);
        if ((raw & signBit) != 0) {
            raw |= -(1L << length); // set the upper bits
        }
        return raw;
    }
}
