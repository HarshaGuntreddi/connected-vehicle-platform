package com.fleet.decoder.dbc;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A lightweight DBC (CAN database) parser.
 *
 * <p>Full DBC is a large format; this parser supports the subset needed by the
 * platform: message definitions ({@code BO_}) and signal definitions
 * ({@code SG_}) with factor/offset, byte order and sign. That covers the bundled
 * {@code sample.dbc} and typical single-multiplex telemetry buses.
 */
public class DbcFile {

    // BO_ <id> <name>: <dlc> <transmitter>
    private static final Pattern BO = Pattern.compile(
            "^BO_\\s+(\\d+)\\s+(\\w+)\\s*:\\s*(\\d+)\\s+(\\w+)");

    // SG_ <name> : <start>|<len>@<order><sign> (<factor>,<offset>) [<min>|<max>] "<unit>" <recv>
    private static final Pattern SG = Pattern.compile(
            "^SG_\\s+(\\w+)\\s*:\\s*(\\d+)\\|(\\d+)@([01])([+-])\\s*\\(([^,]+),([^)]+)\\)\\s*\\[[^\\]]*\\]\\s*\"([^\"]*)\"");

    private final Map<Long, DbcMessage> byId;

    private DbcFile(Map<Long, DbcMessage> byId) {
        this.byId = byId;
    }

    public Optional<DbcMessage> message(long canId) {
        return Optional.ofNullable(byId.get(canId));
    }

    public int messageCount() {
        return byId.size();
    }

    public Map<Long, DbcMessage> messages() {
        return byId;
    }

    /** Parse a DBC document from an input stream. */
    public static DbcFile parse(InputStream in) throws IOException {
        Map<Long, DbcMessage> messages = new HashMap<>();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            long currentId = -1;
            String currentName = null;
            int currentDlc = 0;
            List<DbcSignal> currentSignals = new ArrayList<>();

            while ((line = br.readLine()) != null) {
                String trimmed = line.trim();
                Matcher bo = BO.matcher(trimmed);
                if (bo.find()) {
                    // flush previous message
                    if (currentId >= 0) {
                        messages.put(currentId, new DbcMessage(currentId, currentName, currentDlc, currentSignals));
                    }
                    currentId = Long.parseLong(bo.group(1));
                    currentName = bo.group(2);
                    currentDlc = Integer.parseInt(bo.group(3));
                    currentSignals = new ArrayList<>();
                    continue;
                }
                Matcher sg = SG.matcher(trimmed);
                if (sg.find() && currentId >= 0) {
                    currentSignals.add(new DbcSignal(
                            sg.group(1),
                            Integer.parseInt(sg.group(2)),
                            Integer.parseInt(sg.group(3)),
                            "1".equals(sg.group(4)),
                            "-".equals(sg.group(5)),
                            Double.parseDouble(sg.group(6).trim()),
                            Double.parseDouble(sg.group(7).trim()),
                            sg.group(8)
                    ));
                }
            }
            if (currentId >= 0) {
                messages.put(currentId, new DbcMessage(currentId, currentName, currentDlc, currentSignals));
            }
        }
        return new DbcFile(messages);
    }
}
