package fr.michael.logistics.shipment.infrastructure.persistence;

import fr.michael.logistics.shipment.domain.Anomaly;
import fr.michael.logistics.shipment.domain.AnomalyType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Lecture seule : l'écriture passe par les requêtes de {@link SpringDataAnomalyRepository}. */
@Entity
@Table(name = "anomaly")
class AnomalyJpaEntity {

    @Id
    private UUID id;

    @Column(name = "shipment_id", nullable = false)
    private UUID shipmentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AnomalyType type;

    @Column(name = "detected_at", nullable = false)
    private Instant detectedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    protected AnomalyJpaEntity() {
    }

    Anomaly toDomain() {
        return new Anomaly(id, shipmentId, type, detectedAt, resolvedAt);
    }
}
