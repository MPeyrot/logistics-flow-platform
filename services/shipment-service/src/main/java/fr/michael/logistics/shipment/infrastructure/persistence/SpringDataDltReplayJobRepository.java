package fr.michael.logistics.shipment.infrastructure.persistence;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface SpringDataDltReplayJobRepository extends JpaRepository<DltReplayJobJpaEntity, UUID> {

    @Modifying
    @Query(nativeQuery = true, value = """
            INSERT INTO dlt_replay_job (id, requested_by, requested_at) VALUES (:id, :requestedBy, :requestedAt)""")
    void insert(UUID id, String requestedBy, Instant requestedAt);

    @Modifying
    @Query("UPDATE DltReplayJobJpaEntity j SET j.failed = j.failed + 1 WHERE j.id = :id")
    void incrementFailed(UUID id);

    @Modifying
    @Query("UPDATE DltReplayJobJpaEntity j SET j.finishedAt = :finishedAt, j.error = :error WHERE j.id = :id")
    void finish(UUID id, Instant finishedAt, String error);
}
