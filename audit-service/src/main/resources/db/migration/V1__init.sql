CREATE TABLE audit_log (
    id             BIGSERIAL PRIMARY KEY,
    event_id       VARCHAR(200) NOT NULL UNIQUE,
    kind           VARCHAR(20)  NOT NULL,
    topic          VARCHAR(100),
    event_type     VARCHAR(100),
    aggregate_id   VARCHAR(100),
    correlation_id VARCHAR(100),
    payload        TEXT         NOT NULL,
    error_message  TEXT,
    occurred_at    TIMESTAMPTZ,
    recorded_at    TIMESTAMPTZ  NOT NULL
);
CREATE INDEX idx_audit_correlation ON audit_log (correlation_id);
CREATE INDEX idx_audit_kind_id ON audit_log (kind, id DESC);
