package fr.michael.logistics.shipment.infrastructure.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataScanRepository extends JpaRepository<ScanEventJpaEntity, UUID> {
}
