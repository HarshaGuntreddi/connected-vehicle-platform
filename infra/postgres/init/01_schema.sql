-- ============================================================================
-- Connected Vehicle Platform — database schema
-- Executed automatically by the Postgres image on first startup
-- (mounted into /docker-entrypoint-initdb.d).
-- ============================================================================

-- ---------------------------------------------------------------------------
-- Time-series telemetry. One row per (vehicle, signal, timestamp) sample.
-- Design notes:
--   * Narrow, append-only table -> friendly to high write throughput.
--   * BRIN index on ts: cheap and effective for append-only time-ordered data.
--   * Composite btree (vehicle_id, signal, ts) powers the common query pattern
--     "give me signal X for vehicle Y over time range [a,b]".
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS telemetry (
    id            BIGSERIAL PRIMARY KEY,
    vehicle_id    VARCHAR(64)       NOT NULL,
    message_name  VARCHAR(64),
    signal        VARCHAR(64)       NOT NULL,
    value         DOUBLE PRECISION  NOT NULL,
    ts            TIMESTAMPTZ       NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_telemetry_vehicle_signal_ts
    ON telemetry (vehicle_id, signal, ts DESC);

CREATE INDEX IF NOT EXISTS idx_telemetry_ts_brin
    ON telemetry USING BRIN (ts);

-- ---------------------------------------------------------------------------
-- Diagnostic alerts raised by the predictive-diagnostics service.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS diagnostic_alerts (
    id          BIGSERIAL PRIMARY KEY,
    vehicle_id  VARCHAR(64)   NOT NULL,
    type        VARCHAR(32)   NOT NULL,
    severity    VARCHAR(16)   NOT NULL,
    signal      VARCHAR(64),
    value       DOUBLE PRECISION,
    message     TEXT,
    ts          TIMESTAMPTZ   NOT NULL,
    resolved    BOOLEAN       NOT NULL DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS idx_alerts_vehicle_ts
    ON diagnostic_alerts (vehicle_id, ts DESC);

CREATE INDEX IF NOT EXISTS idx_alerts_active
    ON diagnostic_alerts (vehicle_id) WHERE resolved = FALSE;
