CREATE TABLE quotes (
    order_id       UUID PRIMARY KEY,
    reservation_id UUID          NOT NULL,
    customer_name  VARCHAR(255)  NOT NULL,
    customer_email VARCHAR(255)  NOT NULL,
    line_items     TEXT          NOT NULL,   -- JSON [{name,price}]
    total_amount   NUMERIC(12,2) NOT NULL,
    status         VARCHAR(30)   NOT NULL,
    payment_id     VARCHAR(255),
    preference_id  VARCHAR(255),
    created_at     TIMESTAMP     NOT NULL,
    modified_at    TIMESTAMP     NOT NULL,
    version        INT           NOT NULL DEFAULT 0
);
