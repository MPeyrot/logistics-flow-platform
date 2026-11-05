package fr.michael.logistics.shipment.application;

import fr.michael.logistics.shipment.domain.AnomalyRules;
import fr.michael.logistics.shipment.domain.AnomalyType;
import fr.michael.logistics.shipment.domain.ScanEvent;
import fr.michael.logistics.shipment.domain.Shipment;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RecordScanService {

    private final ShipmentRepository shipments;
    private final ScanRepository scans;
    private final AnomalyRepository anomalies;
    private final Clock clock;

    public RecordScanService(
            ShipmentRepository shipments, ScanRepository scans, AnomalyRepository anomalies, Clock clock) {
        this.shipments = shipments;
        this.scans = scans;
        this.anomalies = anomalies;
        this.clock = clock;
    }

    /**
     * Applique un scan à son expédition. Idempotent : un événement déjà traité (même {@code eventId})
     * est ignoré, car Kafka garantit au moins une livraison, pas exactement une.
     * Dans la même transaction, ferme les anomalies que le scan règle ({@link AnomalyRules#resolvedByScan}) ;
     * un scan refusé lève avant et ne ferme rien.
     */
    @Transactional
    public void record(ScanEvent event) {
        if (scans.existsByEventId(event.eventId())) {
            return;
        }
        Shipment shipment = shipments.lockByReference(event.shipmentReference())
                .orElseThrow(() -> new ShipmentNotFoundException(event.shipmentReference()));
        Shipment scanned = shipment.applyScan(event);
        shipments.save(scanned);
        scans.save(shipment.id(), event);
        Instant now = clock.instant();
        for (AnomalyType type : AnomalyRules.resolvedByScan(scanned)) {
            anomalies.resolveOpen(shipment.id(), type, now);
        }
    }
}
