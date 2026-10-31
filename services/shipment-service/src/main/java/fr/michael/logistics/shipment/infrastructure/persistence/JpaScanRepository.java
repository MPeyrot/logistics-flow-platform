package fr.michael.logistics.shipment.infrastructure.persistence;

import fr.michael.logistics.shipment.application.ScanRepository;
import fr.michael.logistics.shipment.domain.ScanEvent;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
class JpaScanRepository implements ScanRepository {

    private final SpringDataScanRepository jpa;

    JpaScanRepository(SpringDataScanRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public boolean existsByEventId(UUID eventId) {
        return jpa.existsById(eventId);
    }

    @Override
    public void save(UUID shipmentId, ScanEvent event) {
        jpa.save(ScanEventJpaEntity.from(shipmentId, event));
    }
}
