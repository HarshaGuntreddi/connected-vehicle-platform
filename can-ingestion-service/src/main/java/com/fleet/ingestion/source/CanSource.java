package com.fleet.ingestion.source;

import com.fleet.common.dto.CanFrame;

import java.util.function.Consumer;

/**
 * Abstraction over a source of CAN frames. Two implementations exist:
 * a {@link com.fleet.ingestion.source.SimulatorCanSource} that synthesises
 * realistic frames, and a {@link com.fleet.ingestion.source.SocketCanSource}
 * that reads a real (or virtual) SocketCAN interface. The active one is chosen
 * at runtime via the {@code can.mode} property.
 */
public interface CanSource {

    /** Human-readable name of this source (for logging / status). */
    String name();

    /**
     * Begin producing frames. Each frame is handed to {@code sink}. Simulator
     * sources are driven by a scheduler; hardware sources spawn a reader thread.
     */
    void start(Consumer<CanFrame> sink);

    /** Stop producing and release resources. */
    void stop();
}
