CREATE TABLE payments (
    id              UUID PRIMARY KEY,
    sender_id       VARCHAR(64)    NOT NULL,
    sender_name     VARCHAR(100),
    receiver_id     VARCHAR(64)    NOT NULL,
    receiver_name   VARCHAR(100),
    amount          NUMERIC(19, 2) NOT NULL CHECK (amount > 0),
    currency        VARCHAR(3)     NOT NULL,
    note            VARCHAR(140),
    status          VARCHAR(20)    NOT NULL,
    failure_reason  VARCHAR(100),
    idempotency_key VARCHAR(100)   NOT NULL,
    attempts        INT            NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ    NOT NULL,
    updated_at      TIMESTAMPTZ    NOT NULL,
    version         BIGINT         NOT NULL DEFAULT 0,
    -- the database is the final guard against duplicate payments
    CONSTRAINT uq_payment_idempotency UNIQUE (sender_id, idempotency_key)
);
CREATE INDEX idx_payments_sender   ON payments (sender_id, created_at DESC);
CREATE INDEX idx_payments_receiver ON payments (receiver_id, created_at DESC);
CREATE INDEX idx_payments_pending  ON payments (status, created_at) WHERE status = 'PENDING';

CREATE TABLE outbox_event (
    id           UUID PRIMARY KEY,
    topic        VARCHAR(100) NOT NULL,
    event_key    VARCHAR(100) NOT NULL,
    payload      TEXT         NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL,
    published_at TIMESTAMPTZ
);
CREATE INDEX idx_outbox_unpublished ON outbox_event (created_at) WHERE published_at IS NULL;
