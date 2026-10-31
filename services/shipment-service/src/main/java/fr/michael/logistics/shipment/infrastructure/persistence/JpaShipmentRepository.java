package fr.michael.logistics.shipment.infrastructure.persistence;

import fr.michael.logistics.shipment.application.ShipmentRepository;
import fr.michael.logistics.shipment.domain.Shipment;
import java.util.List;
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
    public boolean existsByReference(String reference) {
        return jpa.existsByReference(reference);
    }
}
