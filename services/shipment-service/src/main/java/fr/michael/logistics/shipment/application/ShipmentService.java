package fr.michael.logistics.shipment.application;

import fr.michael.logistics.shipment.domain.Anomaly;
import fr.michael.logistics.shipment.domain.Shipment;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ShipmentService {

    private final ShipmentRepository repository;
    private final AnomalyRepository anomalies;
    private final Clock clock;

    public ShipmentService(ShipmentRepository repository, AnomalyRepository anomalies, Clock clock) {
        this.repository = repository;
        this.anomalies = anomalies;
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
    public List<ShipmentWithAnomalies> list() {
        Map<UUID, List<Anomaly>> openByShipment = anomalies.findOpen().stream()
                .collect(Collectors.groupingBy(Anomaly::shipmentId));
        return repository.findAll().stream()
                .map(shipment -> new ShipmentWithAnomalies(
                        shipment, openByShipment.getOrDefault(shipment.id(), List.of())))
                .toList();
    }
}
