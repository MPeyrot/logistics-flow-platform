package fr.michael.logistics.shipment.infrastructure.persistence;

import fr.michael.logistics.shipment.domain.ShipmentStatus;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface SpringDataShipmentRepository extends JpaRepository<ShipmentJpaEntity, UUID> {

    Optional<ShipmentJpaEntity> findByReference(String reference);

    /** SELECT … FOR UPDATE : verrou tenu jusqu'à la fin de la transaction. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ShipmentJpaEntity> findWithLockByReference(String reference);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ShipmentJpaEntity> findWithLockById(UUID id);

    /** Voir {@code ShipmentRepository#findAnomalyCandidateIds} ; {@code status} : le statut terminal à exclure. */
    @Query("""
            select s.id from ShipmentJpaEntity s
            where s.status <> :status
              and ((s.expectedDeliveryAt < :now
                    and not exists (select 1 from AnomalyJpaEntity a
                        where a.shipmentId = s.id and a.type = fr.michael.logistics.shipment.domain.AnomalyType.LATE
                          and a.resolvedAt is null))
                or (coalesce((select max(e.receivedAt) from ScanEventJpaEntity e where e.shipmentId = s.id), s.createdAt)
                        <= :stalledBefore
                    and not exists (select 1 from AnomalyJpaEntity a
                        where a.shipmentId = s.id and a.type = fr.michael.logistics.shipment.domain.AnomalyType.STALLED
                          and a.resolvedAt is null)))""")
    List<UUID> findAnomalyCandidateIds(ShipmentStatus status, Instant now, Instant stalledBefore);

    boolean existsByReference(String reference);
}
