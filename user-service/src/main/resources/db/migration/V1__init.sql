CREATE TABLE users (
    id           VARCHAR(64)  PRIMARY KEY,
    username     VARCHAR(100) NOT NULL,
    email        VARCHAR(200),
    display_name VARCHAR(200),
    created_at   TIMESTAMPTZ  NOT NULL
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

-- demo customers (same ids as in the Keycloak realm import)
INSERT INTO users (id, username, email, display_name, created_at) VALUES
 ('aaaaaaaa-0000-0000-0000-000000000001', 'alice',    'alice@finflow.dev',    'Alice Sharma',        now()),
 ('aaaaaaaa-0000-0000-0000-000000000002', 'bob',      'bob@finflow.dev',      'Bob Verma',           now()),
 ('aaaaaaaa-0000-0000-0000-000000000003', 'merchant', 'merchant@finflow.dev', 'Bangalore Coffee Co.', now()),
 ('aaaaaaaa-0000-0000-0000-000000000004', 'admin',    'admin@finflow.dev',    'Ops Admin',           now());
