package fr.michael.logistics.shipment.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Lignes écrites par une requête native (identifiant attribué par la base, dans l'ordre d'écriture). */
@Entity
@Table(name = "dlt_replay_outbox")
class DltReplayOutboxJpaEntity {

    @Id
    private long id;

    @Column(name = "dlt_partition", nullable = false)
    private int dltPartition;

    @Column(name = "dlt_offset", nullable = false)
    private long dltOffset;

    @Column(name = "message_key", columnDefinition = "text")
    private String messageKey;

    @Column(name = "message_value", columnDefinition = "text")
    private String messageValue;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "job_id")
    private UUID jobId;

    protected DltReplayOutboxJpaEntity() {
    }

    long id() {
        return id;
    }

    String messageKey() {
        return messageKey;
    }

    String messageValue() {
        return messageValue;
    }
}
