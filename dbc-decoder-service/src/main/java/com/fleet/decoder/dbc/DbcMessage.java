package com.fleet.decoder.dbc;

import java.util.List;

/**
 * One message definition parsed from a DBC {@code BO_} line, with its signals.
 *
 * @param canId   arbitration ID
 * @param name    message name, e.g. "EngineData"
 * @param dlc     data length in bytes
 * @param signals signals carried by this message
 */
public record DbcMessage(long canId, String name, int dlc, List<DbcSignal> signals) {
}
