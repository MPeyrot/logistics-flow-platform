package fr.michael.logistics.shipment.infrastructure.persistence;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface SpringDataDltReplayOutboxRepository extends JpaRepository<DltReplayOutboxJpaEntity, Long> {

    /** Clé du verrou consultatif du publieur (pg_advisory), propre à ce service. */
    long PUBLISHER_LOCK = 0x5343414e444c54L; // "SCANDLT"

    @Modifying
    @Query(nativeQuery = true, value = """
            INSERT INTO dlt_replay_outbox (dlt_partition, dlt_offset, message_key, message_value, created_at, job_id)
            VALUES (:partition, :offset, :key, :value, :createdAt, :jobId)""")
    void insert(int partition, long offset, String key, String value, Instant createdAt, UUID jobId);

    /** Tenu jusqu'à la fin de la transaction ; {@code false} si une autre transaction le tient. */
    @Query(nativeQuery = true, value = "SELECT pg_try_advisory_xact_lock(:lock)")
    boolean tryLock(long lock);

    @Query(nativeQuery = true, value = """
            SELECT * FROM dlt_replay_outbox WHERE published_at IS NULL ORDER BY id LIMIT :limit""")
    List<DltReplayOutboxJpaEntity> findPending(int limit);

    @Modifying
    @Query("UPDATE DltReplayOutboxJpaEntity o SET o.publishedAt = :publishedAt WHERE o.id = :id")
    void markPublished(long id, Instant publishedAt);

    @Query(nativeQuery = true, value = """
            SELECT count(*) AS replayed, count(published_at) AS published FROM dlt_replay_outbox WHERE job_id = :jobId""")
    Counts countByJob(UUID jobId);

    interface Counts {
        long getReplayed();

        long getPublished();
    }
}
