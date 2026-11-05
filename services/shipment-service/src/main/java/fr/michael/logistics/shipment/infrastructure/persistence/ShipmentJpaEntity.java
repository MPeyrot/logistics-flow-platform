package fr.michael.logistics.shipment.infrastructure.persistence;

import fr.michael.logistics.shipment.domain.Shipment;
import fr.michael.logistics.shipment.domain.ShipmentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "shipment")
class ShipmentJpaEntity {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String reference;

    @Column(nullable = false)
    private String origin;

    @Column(nullable = false)
    private String destination;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ShipmentStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expected_delivery_at")
    private Instant expectedDeliveryAt;

    protected ShipmentJpaEntity() {
    }

    static ShipmentJpaEntity from(Shipment shipment) {
        ShipmentJpaEntity entity = new ShipmentJpaEntity();
        entity.id = shipment.id();
        entity.reference = shipment.reference();
        entity.origin = shipment.origin();
        entity.destination = shipment.destination();
        entity.status = shipment.status();
        entity.createdAt = shipment.createdAt();
        entity.expectedDeliveryAt = shipment.expectedDeliveryAt();
        return entity;
    }

    Shipment toDomain() {
        return new Shipment(id, reference, origin, destination, status, createdAt, expectedDeliveryAt);
    }
}
