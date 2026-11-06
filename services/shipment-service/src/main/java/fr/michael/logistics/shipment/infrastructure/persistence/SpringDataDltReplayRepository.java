package fr.michael.logistics.shipment.infrastructure.persistence;

import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface SpringDataDltReplayRepository extends JpaRepository<DltReplayJpaEntity, DltReplayJpaEntity.Key> {

    /**
     * 1 si le rejeu est enregistré, 0 s'il l'était déjà. Un rejeu concurrent du même message, pas encore commité,
     * fait attendre cette insertion jusqu'à son commit (0) ou son annulation (1).
     */
    @Modifying
    @Query(nativeQuery = true, value = """
            INSERT INTO dlt_replay (dlt_partition, dlt_offset, replayed_at, replayed_by)
            VALUES (:partition, :offset, :replayedAt, :replayedBy)
            ON CONFLICT (dlt_partition, dlt_offset) DO NOTHING""")
    int insertIfAbsent(int partition, long offset, Instant replayedAt, String replayedBy);
}
