-- Cycle de vie des expéditions : date de livraison prévue, motif d'échec, anomalies, rejeux du DLT.
-- expected_delivery_at reste NULL pour les expéditions déjà en base : elles ne sont jamais LATE.
ALTER TABLE shipment ADD COLUMN expected_delivery_at TIMESTAMPTZ;

ALTER TABLE scan_event ADD COLUMN reason VARCHAR(200);

CREATE TABLE anomaly (
    id          UUID PRIMARY KEY,
    shipment_id UUID        NOT NULL REFERENCES shipment (id),
    type        VARCHAR(20) NOT NULL,
    detected_at TIMESTAMPTZ NOT NULL,
    resolved_at TIMESTAMPTZ
);
-- Une seule anomalie ouverte par expédition et type, même avec plusieurs instances.
CREATE UNIQUE INDEX uq_anomaly_open ON anomaly (shipment_id, type) WHERE resolved_at IS NULL;
CREATE INDEX idx_anomaly_detected_at ON anomaly (detected_at);

CREATE TABLE dlt_replay (
    dlt_partition INT         NOT NULL,
    dlt_offset    BIGINT      NOT NULL,
    replayed_at   TIMESTAMPTZ NOT NULL,
    replayed_by   VARCHAR(50) NOT NULL,
    PRIMARY KEY (dlt_partition, dlt_offset)
);
