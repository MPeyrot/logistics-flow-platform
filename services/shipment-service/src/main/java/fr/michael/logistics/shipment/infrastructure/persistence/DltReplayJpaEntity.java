package fr.michael.logistics.shipment.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;

@Entity
@Table(name = "dlt_replay")
@IdClass(DltReplayJpaEntity.Key.class)
class DltReplayJpaEntity {

    @Id
    @Column(name = "dlt_partition")
    private int dltPartition;

    @Id
    @Column(name = "dlt_offset")
    private long dltOffset;

    @Column(name = "replayed_at", nullable = false)
    private Instant replayedAt;

    @Column(name = "replayed_by", nullable = false)
    private String replayedBy;

    protected DltReplayJpaEntity() {
    }

    int dltPartition() {
        return dltPartition;
    }

    long dltOffset() {
        return dltOffset;
    }

    Instant replayedAt() {
        return replayedAt;
    }

    record Key(int dltPartition, long dltOffset) implements Serializable {
    }
}
