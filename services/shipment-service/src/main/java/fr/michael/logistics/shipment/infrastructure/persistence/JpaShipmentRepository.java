package fr.michael.logistics.shipment.infrastructure.persistence;

import fr.michael.logistics.shipment.application.ShipmentActivity;
import fr.michael.logistics.shipment.application.ShipmentRepository;
import fr.michael.logistics.shipment.domain.Shipment;
import fr.michael.logistics.shipment.domain.ShipmentStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

@Repository
class JpaShipmentRepository implements ShipmentRepository {

    private final SpringDataShipmentRepository jpa;

    JpaShipmentRepository(SpringDataShipmentRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Shipment save(Shipment shipment) {
        return jpa.save(ShipmentJpaEntity.from(shipment)).toDomain();
    }

    @Override
    public List<Shipment> findAll() {
        return jpa.findAll(Sort.by(Sort.Direction.DESC, "createdAt")).stream()
                .map(ShipmentJpaEntity::toDomain)
                .toList();
    }

    @Override
    public Optional<Shipment> findByReference(String reference) {
        return jpa.findByReference(reference).map(ShipmentJpaEntity::toDomain);
    }

    @Override
    public Optional<Shipment> lockByReference(String reference) {
        return jpa.findWithLockByReference(reference).map(ShipmentJpaEntity::toDomain);
    }

    @Override
    public Optional<Shipment> lockById(UUID id) {
        return jpa.findWithLockById(id).map(ShipmentJpaEntity::toDomain);
    }

    @Override
    public List<ShipmentActivity> findUndeliveredActivity() {
        return jpa.findWithLastScanByStatusNot(ShipmentStatus.DELIVERED).stream()
                .map(row -> new ShipmentActivity(((ShipmentJpaEntity) row[0]).toDomain(), (Instant) row[1]))
                .toList();
    }

    @Override
    public boolean existsByReference(String reference) {
        return jpa.existsByReference(reference);
    }
}
