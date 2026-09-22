CREATE TABLE wallets (
    user_id    VARCHAR(64)    PRIMARY KEY,
    username   VARCHAR(100),
    balance    NUMERIC(19, 2) NOT NULL CHECK (balance >= 0),
    currency   VARCHAR(3)     NOT NULL DEFAULT 'INR',
    frozen     BOOLEAN        NOT NULL DEFAULT FALSE,
    version    BIGINT         NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ
);

CREATE TABLE ledger_entries (
    id            UUID PRIMARY KEY,
    user_id       VARCHAR(64)    NOT NULL,
    payment_id    VARCHAR(64),
    direction     VARCHAR(10)    NOT NULL,
    amount        NUMERIC(19, 2) NOT NULL,
    balance_after NUMERIC(19, 2) NOT NULL,
    counterparty  VARCHAR(200),
    created_at    TIMESTAMPTZ    NOT NULL
);
CREATE INDEX idx_ledger_user_time ON ledger_entries (user_id, created_at DESC);

-- one row per processed payment: makes the gRPC Transfer call idempotent
CREATE TABLE transfer_log (
    payment_id   VARCHAR(64) PRIMARY KEY,
    from_user_id VARCHAR(64),
    to_user_id   VARCHAR(64),
    amount       NUMERIC(19, 2),
    created_at   TIMESTAMPTZ
);

CREATE TABLE outbox_event (
    id           UUID PRIMARY KEY,
    topic        VARCHAR(100) NOT NULL,
    event_key    VARCHAR(100) NOT NULL,
    payload      TEXT         NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL,
    published_at TIMESTAMPTZ
);
CREATE INDEX idx_outbox_unpublished ON outbox_event (created_at) WHERE published_at IS NULL;

-- demo wallets (same user ids as the Keycloak realm import)
INSERT INTO wallets (user_id, username, balance, currency, updated_at) VALUES
 ('aaaaaaaa-0000-0000-0000-000000000001', 'alice',    25000.00, 'INR', now()),
 ('aaaaaaaa-0000-0000-0000-000000000002', 'bob',      10000.00, 'INR', now()),
 ('aaaaaaaa-0000-0000-0000-000000000003', 'merchant',  5000.00, 'INR', now()),
 ('aaaaaaaa-0000-0000-0000-000000000004', 'admin',     1000.00, 'INR', now());
