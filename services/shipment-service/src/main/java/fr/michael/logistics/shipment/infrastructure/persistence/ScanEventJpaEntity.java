package fr.michael.logistics.shipment.infrastructure.persistence;

import fr.michael.logistics.shipment.domain.ScanEvent;
import fr.michael.logistics.shipment.domain.ScanType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "scan_event")
class ScanEventJpaEntity {

    @Id
    @Column(name = "event_id")
    private UUID eventId;

    @Column(name = "shipment_id", nullable = false)
    private UUID shipmentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ScanType type;

    @Column(nullable = false)
    private String site;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected ScanEventJpaEntity() {
    }

    static ScanEventJpaEntity from(UUID shipmentId, ScanEvent event) {
        ScanEventJpaEntity entity = new ScanEventJpaEntity();
        entity.eventId = event.eventId();
        entity.shipmentId = shipmentId;
        entity.type = event.type();
        entity.site = event.site();
        entity.occurredAt = event.occurredAt();
        return entity;
    }
}
