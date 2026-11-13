package fr.michael.logistics.shipment.application;

import fr.michael.logistics.shipment.domain.Anomaly;
import java.util.List;
import java.util.UUID;

/** Port de sortie : ce qui s'est passé sur une expédition, en lecture seule. */
public interface ShipmentHistory {

    /** Scans par {@code occurredAt} puis {@code receivedAt}. */
    List<ScanRecord> scans(UUID shipmentId);

    /** Toutes les anomalies, ouvertes et closes, détectées en premier d'abord. */
    List<Anomaly> anomalies(UUID shipmentId);
}
