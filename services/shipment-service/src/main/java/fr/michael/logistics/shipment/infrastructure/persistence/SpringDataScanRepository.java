package fr.michael.logistics.shipment.infrastructure.persistence;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface SpringDataScanRepository extends JpaRepository<ScanEventJpaEntity, UUID> {

    @Query("select max(e.receivedAt) from ScanEventJpaEntity e where e.shipmentId = :shipmentId")
    Optional<Instant> findLastReceivedAt(UUID shipmentId);
}
