package fr.michael.logistics.shipment.infrastructure.persistence;

import fr.michael.logistics.shipment.application.AnomalyRepository;
import fr.michael.logistics.shipment.application.AnomalyView;
import fr.michael.logistics.shipment.domain.Anomaly;
import fr.michael.logistics.shipment.domain.AnomalyType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
class JpaAnomalyRepository implements AnomalyRepository {

    private final SpringDataAnomalyRepository jpa;

    JpaAnomalyRepository(SpringDataAnomalyRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public boolean openIfAbsent(Anomaly anomaly) {
        return jpa.insertUnlessOpen(
                anomaly.id(), anomaly.shipmentId(), anomaly.type().name(), anomaly.detectedAt()) == 1;
    }

    @Override
    public void resolveOpen(UUID shipmentId, AnomalyType type, Instant resolvedAt) {
        jpa.resolveOpen(shipmentId, type, resolvedAt);
    }

    @Override
    public List<AnomalyView> findViews(Boolean open) {
        return jpa.findViews(open);
    }

    @Override
    public List<Anomaly> findOpen() {
        return jpa.findByResolvedAtIsNull().stream().map(AnomalyJpaEntity::toDomain).toList();
    }
}
