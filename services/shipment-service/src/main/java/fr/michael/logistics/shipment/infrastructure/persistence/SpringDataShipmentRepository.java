package fr.michael.logistics.shipment.infrastructure.persistence;

import fr.michael.logistics.shipment.domain.ShipmentStatus;
import jakarta.persistence.LockModeType;
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

    /** Lignes [expédition, heure de réception du dernier scan ou null]. */
    @Query("""
            select s, (select max(e.receivedAt) from ScanEventJpaEntity e where e.shipmentId = s.id)
            from ShipmentJpaEntity s
            where s.status <> :status""")
    List<Object[]> findWithLastScanByStatusNot(ShipmentStatus status);

    boolean existsByReference(String reference);
}
