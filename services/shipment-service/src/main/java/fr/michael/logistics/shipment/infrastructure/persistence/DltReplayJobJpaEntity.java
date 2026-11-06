package fr.michael.logistics.shipment.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "dlt_replay_job")
class DltReplayJobJpaEntity {

    @Id
    private UUID id;

    @Column(name = "requested_by", nullable = false)
    private String requestedBy;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(nullable = false)
    private int failed;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(length = 500)
    private String error;

    protected DltReplayJobJpaEntity() {
    }

    UUID id() {
        return id;
    }

    String requestedBy() {
        return requestedBy;
    }

    Instant requestedAt() {
        return requestedAt;
    }

    int failed() {
        return failed;
    }

    Instant finishedAt() {
        return finishedAt;
    }

    String error() {
        return error;
    }
}
