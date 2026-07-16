package com.fleet.decoder.web;

import com.fleet.common.dto.CanFrame;
import com.fleet.common.dto.DecodedTelemetry;
import com.fleet.decoder.dbc.CanDecoder;
import com.fleet.decoder.dbc.DbcFile;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Introspection and ad-hoc decode endpoints for the DBC decoder.
 */
@RestController
@RequestMapping("/api/decoder")
@Tag(name = "DBC Decoder", description = "Inspect the loaded DBC and decode frames on demand")
public class DecoderController {

    private final DbcFile dbc;
    private final CanDecoder decoder;

    public DecoderController(DbcFile dbc, CanDecoder decoder) {
        this.dbc = dbc;
        this.decoder = decoder;
    }

    @GetMapping("/messages")
    @Operation(summary = "List message and signal definitions loaded from the DBC")
    public Map<Long, Object> messages() {
        return dbc.messages().entrySet().stream().collect(
                java.util.stream.Collectors.toMap(
                        Map.Entry::getKey,
                        e -> Map.of(
                                "name", e.getValue().name(),
                                "dlc", e.getValue().dlc(),
                                "signals", e.getValue().signals().stream().map(s -> Map.of(
                                        "name", s.name(),
                                        "startBit", s.startBit(),
                                        "length", s.length(),
                                        "factor", s.factor(),
                                        "offset", s.offset(),
                                        "unit", s.unit()
                                )).toList()
                        )));
    }

    @PostMapping("/decode")
    @Operation(summary = "Decode a single raw frame (for testing). Provide canId, dlc and hex data.")
    public ResponseEntity<DecodedTelemetry> decode(@RequestBody DecodeRequest req) {
        CanFrame frame = new CanFrame(
                req.vehicleId() != null ? req.vehicleId() : "TEST",
                req.canId(), req.dlc(), req.data(), Instant.now());
        return decoder.decode(frame)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.unprocessableEntity().build());
    }

    /** Body for POST /decode. */
    public record DecodeRequest(String vehicleId, long canId, int dlc, String data) {
    }
}
