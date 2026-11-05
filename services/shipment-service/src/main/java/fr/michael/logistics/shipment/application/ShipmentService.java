package fr.michael.logistics.shipment.application;

import fr.michael.logistics.shipment.domain.Shipment;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ShipmentService {

    private final ShipmentRepository repository;
    private final Clock clock;

    public ShipmentService(ShipmentRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public Shipment create(String reference, String origin, String destination, Instant expectedDeliveryAt) {
        if (repository.existsByReference(reference)) {
            throw new DuplicateReferenceException(reference);
        }
        return repository.save(Shipment.create(reference, origin, destination, expectedDeliveryAt, clock.instant()));
    }

    @Transactional(readOnly = true)
    public List<Shipment> list() {
        return repository.findAll();
    }
}
