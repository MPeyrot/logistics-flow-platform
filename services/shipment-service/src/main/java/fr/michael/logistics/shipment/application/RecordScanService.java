package fr.michael.logistics.shipment.application;

import fr.michael.logistics.shipment.domain.ScanEvent;
import fr.michael.logistics.shipment.domain.Shipment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RecordScanService {

    private final ShipmentRepository shipments;
    private final ScanRepository scans;

    public RecordScanService(ShipmentRepository shipments, ScanRepository scans) {
        this.shipments = shipments;
        this.scans = scans;
    }

    /**
     * Applique un scan à son expédition. Idempotent : un événement déjà traité (même {@code eventId})
     * est ignoré, car Kafka garantit au moins une livraison, pas exactement une.
     */
    @Transactional
    public void record(ScanEvent event) {
        if (scans.existsByEventId(event.eventId())) {
            return;
        }
        Shipment shipment = shipments.findByReference(event.shipmentReference())
                .orElseThrow(() -> new ShipmentNotFoundException(event.shipmentReference()));
        shipments.save(shipment.applyScan(event));
        scans.save(shipment.id(), event);
    }
}
