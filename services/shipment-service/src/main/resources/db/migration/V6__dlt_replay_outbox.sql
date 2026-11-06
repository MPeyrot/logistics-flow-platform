-- Rejeux du DLT : « tout rejouer » en tâche de fond, suivi par l'ADMIN, et outbox des messages à republier.
CREATE TABLE dlt_replay_job (
    id           UUID         PRIMARY KEY,
    requested_by VARCHAR(50)  NOT NULL,
    requested_at TIMESTAMPTZ  NOT NULL,
    -- messages dont l'enregistrement a échoué (base indisponible) : ils restent à rejouer
    failed       INT          NOT NULL DEFAULT 0,
    finished_at  TIMESTAMPTZ,
    -- parcours du DLT interrompu (broker injoignable)
    error        VARCHAR(500)
);

-- Écrite avec dlt_replay, dans la même transaction ; publiée vers scan-events après validation, dans l'ordre de id.
CREATE TABLE dlt_replay_outbox (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    dlt_partition INT         NOT NULL,
    dlt_offset    BIGINT      NOT NULL,
    message_key   TEXT,
    message_value TEXT,
    created_at    TIMESTAMPTZ NOT NULL,
    published_at  TIMESTAMPTZ,
    -- NULL : rejeu d'un seul message
    job_id        UUID REFERENCES dlt_replay_job (id),
    UNIQUE (dlt_partition, dlt_offset),
    FOREIGN KEY (dlt_partition, dlt_offset) REFERENCES dlt_replay (dlt_partition, dlt_offset)
);
CREATE INDEX idx_dlt_replay_outbox_pending ON dlt_replay_outbox (id) WHERE published_at IS NULL;
CREATE INDEX idx_dlt_replay_outbox_job ON dlt_replay_outbox (job_id);
