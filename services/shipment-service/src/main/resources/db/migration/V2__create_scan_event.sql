-- Historique des scans reçus. La clé primaire event_id rend le traitement idempotent :
-- un événement Kafka rejoué n'est appliqué qu'une fois.
CREATE TABLE scan_event (
    event_id    UUID PRIMARY KEY,
    shipment_id UUID         NOT NULL REFERENCES shipment (id),
    type        VARCHAR(30)  NOT NULL,
    site        VARCHAR(100) NOT NULL,
    occurred_at TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_scan_event_shipment ON scan_event (shipment_id, occurred_at);
