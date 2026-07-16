package com.fleet.ingestion.source;

import com.fleet.common.dto.CanFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads real CAN traffic from a Linux SocketCAN interface (e.g. {@code vcan0}).
 *
 * <p>Rather than depend on native JNI bindings (which complicate a portable,
 * containerised build), this source shells out to {@code candump} from the
 * {@code can-utils} package and parses its log format:
 * <pre>  (1700000000.123456) vcan0 100#1122334455667788</pre>
 * The interface must exist on the host and be visible to the container (run the
 * ingestion service with {@code network_mode: host} and {@code --cap-add} for
 * real hardware; see the README). This path is only active when
 * {@code CAN_MODE=hardware}; the simulator is the default.
 */
@Component
@ConditionalOnProperty(name = "can.mode", havingValue = "hardware")
public class SocketCanSource implements CanSource {

    private static final Logger log = LoggerFactory.getLogger(SocketCanSource.class);

    // Matches candump -L output: (ts) iface ID#DATA  (also tolerates no data)
    private static final Pattern LINE = Pattern.compile(
            "\\(([0-9.]+)\\)\\s+(\\S+)\\s+([0-9A-Fa-f]+)#([0-9A-Fa-f]*)");

    private final String iface;
    private final String vehicleId;
    private volatile boolean running;
    private Process process;

    public SocketCanSource(@Value("${can.interface:vcan0}") String iface,
                           @Value("${can.vehicle-id:VIN-HW-01}") String vehicleId) {
        this.iface = iface;
        this.vehicleId = vehicleId;
    }

    @Override
    public String name() {
        return "socketcan(interface=" + iface + ")";
    }

    @Override
    public void start(Consumer<CanFrame> sink) {
        running = true;
        Thread reader = new Thread(() -> readLoop(sink), "socketcan-reader");
        reader.setDaemon(true);
        reader.start();
        log.info("SocketCAN source started on interface {} (via candump)", iface);
    }

    private void readLoop(Consumer<CanFrame> sink) {
        try {
            // -L = log format with absolute timestamp; parse each line
            process = new ProcessBuilder("candump", "-L", iface)
                    .redirectErrorStream(true)
                    .start();
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while (running && (line = br.readLine()) != null) {
                    parse(line).ifPresent(sink);
                }
            }
        } catch (Exception e) {
            log.error("candump reader failed for {} — is can-utils installed and {} up? {}",
                    iface, iface, e.getMessage());
        }
    }

    private java.util.Optional<CanFrame> parse(String line) {
        Matcher m = LINE.matcher(line.trim());
        if (!m.matches()) {
            return java.util.Optional.empty();
        }
        try {
            long canId = Long.parseLong(m.group(3), 16);
            String data = m.group(4).toUpperCase();
            int dlc = data.length() / 2;
            return java.util.Optional.of(new CanFrame(vehicleId, canId, dlc, data, Instant.now()));
        } catch (NumberFormatException e) {
            return java.util.Optional.empty();
        }
    }

    @Override
    public void stop() {
        running = false;
        if (process != null) {
            process.destroy();
        }
    }
}
