package com.fleet.decoder.dbc;

import java.util.Map;

/**
 * Minimal catalogue mapping raw fault-code numbers to human-readable DTC
 * (Diagnostic Trouble Code) descriptions. In a real system this would be a far
 * larger table sourced from OEM data; a handful is enough for the demo.
 */
public final class DtcCatalog {

    private DtcCatalog() {
    }

    private static final Map<Integer, String> CODES = Map.of(
            0x0217, "P0217 Engine Overtemperature Condition",
            0x0128, "P0128 Coolant Thermostat Below Regulating Temperature",
            0x0562, "P0562 System Voltage Low",
            0x0300, "P0300 Random/Multiple Cylinder Misfire Detected"
    );

    /** Return "Pxxxx description" if known, otherwise a generic hex label. */
    public static String describe(int rawCode) {
        String known = CODES.get(rawCode);
        return known != null ? known : String.format("DTC-0x%04X (unknown)", rawCode);
    }
}
