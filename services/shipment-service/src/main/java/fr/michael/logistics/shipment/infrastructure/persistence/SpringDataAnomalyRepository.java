package fr.michael.logistics.shipment.infrastructure.persistence;

import fr.michael.logistics.shipment.application.AnomalyView;
import fr.michael.logistics.shipment.domain.AnomalyType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

interface SpringDataAnomalyRepository extends JpaRepository<AnomalyJpaEntity, UUID> {

    /**
     * L'index unique partiel {@code uq_anomaly_open} (V4) tranche entre deux insertions concurrentes :
     * la seconde ne fait rien (0 ligne) au lieu d'échouer, et sa transaction reste utilisable.
     */
    @Transactional
    @Modifying
    @Query(value = """
            INSERT INTO anomaly (id, shipment_id, type, detected_at)
            VALUES (:id, :shipmentId, :type, :detectedAt)
            ON CONFLICT (shipment_id, type) WHERE resolved_at IS NULL DO NOTHING""", nativeQuery = true)
    int insertUnlessOpen(UUID id, UUID shipmentId, String type, Instant detectedAt);

    @Transactional
    @Modifying
    @Query("""
            update AnomalyJpaEntity a set a.resolvedAt = :resolvedAt
            where a.shipmentId = :shipmentId and a.type = :type and a.resolvedAt is null""")
    int resolveOpen(UUID shipmentId, AnomalyType type, Instant resolvedAt);

    List<AnomalyJpaEntity> findByResolvedAtIsNull();

    List<AnomalyJpaEntity> findByShipmentIdOrderByDetectedAtAsc(UUID shipmentId);

    List<AnomalyJpaEntity> findByShipmentIdAndResolvedAtIsNull(UUID shipmentId);

    @Query("""
            select new fr.michael.logistics.shipment.application.AnomalyView(
                a.id, s.reference, a.type, a.detectedAt, a.resolvedAt)
            from AnomalyJpaEntity a join ShipmentJpaEntity s on s.id = a.shipmentId
            where :open is null or (:open = true and a.resolvedAt is null) or (:open = false and a.resolvedAt is not null)
            order by a.detectedAt desc""")
    List<AnomalyView> findViews(Boolean open);
}
