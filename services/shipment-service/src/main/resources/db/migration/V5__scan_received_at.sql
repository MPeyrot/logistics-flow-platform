-- Heure à laquelle shipment-service a reçu le scan (occurred_at reste la date métier, fournie par le client).
-- STALLED compte depuis le dernier scan reçu : un scan en retard (scanner hors ligne, rejeu du DLT) ou daté
-- dans le futur ne fausse plus la détection.
-- Scans déjà en base : heure de réception inconnue, on reprend occurred_at (la règle d'avant pour eux).
ALTER TABLE scan_event ADD COLUMN received_at TIMESTAMPTZ;
UPDATE scan_event SET received_at = occurred_at;
ALTER TABLE scan_event ALTER COLUMN received_at SET NOT NULL;

CREATE INDEX idx_scan_event_shipment_received ON scan_event (shipment_id, received_at);
