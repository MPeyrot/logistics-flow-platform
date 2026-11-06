package fr.michael.logistics.shipment.infrastructure.persistence;

import fr.michael.logistics.shipment.application.ScanRepository;
import fr.michael.logistics.shipment.domain.ScanEvent;
import java.time.Instant;
import java.util.Optional;
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
    public void save(UUID shipmentId, ScanEvent event, Instant receivedAt) {
        jpa.save(ScanEventJpaEntity.from(shipmentId, event, receivedAt));
    }

    @Override
    public Optional<Instant> lastReceivedAt(UUID shipmentId) {
        return jpa.findLastReceivedAt(shipmentId);
    }
}
