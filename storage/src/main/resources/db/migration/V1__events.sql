CREATE TABLE events (
    id UUID PRIMARY KEY,
    created_at TIMESTAMP NOT NULL,
    modified_at TIMESTAMP NOT NULL,
    version INT NOT NULL DEFAULT 0,
    type VARCHAR(255) NOT NULL,
    payload TEXT NOT NULL
);

CREATE INDEX idx_events_created_at ON events (created_at);
