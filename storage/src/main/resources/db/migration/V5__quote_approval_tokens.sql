CREATE TABLE quote_approval_tokens (
    id          UUID PRIMARY KEY,
    order_id    UUID      NOT NULL REFERENCES quotes(order_id),
    expires_at  TIMESTAMP NOT NULL,
    used_at     TIMESTAMP,
    created_at  TIMESTAMP NOT NULL,
    modified_at TIMESTAMP NOT NULL,
    version     INT       NOT NULL DEFAULT 0
);

CREATE INDEX idx_quote_approval_tokens_order_id ON quote_approval_tokens (order_id);
