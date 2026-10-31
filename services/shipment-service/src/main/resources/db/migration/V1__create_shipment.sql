CREATE TABLE shipment (
    id          UUID PRIMARY KEY,
    reference   VARCHAR(50)  NOT NULL UNIQUE,
    origin      VARCHAR(100) NOT NULL,
    destination VARCHAR(100) NOT NULL,
    status      VARCHAR(20)  NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL
);
