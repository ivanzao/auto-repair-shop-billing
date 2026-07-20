CREATE TABLE idempotency (
    id             UUID PRIMARY KEY,
    entity_id      UUID      NOT NULL,
    idempotency_id UUID      NOT NULL,
    created_at     TIMESTAMP NOT NULL,
    CONSTRAINT uk_idempotency UNIQUE (entity_id, idempotency_id)
);

CREATE INDEX idx_idempotency_entity ON idempotency (entity_id);
