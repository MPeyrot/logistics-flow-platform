package fr.michael.logistics.shipment.application;

import fr.michael.logistics.shipment.domain.ScanEvent;
import java.util.UUID;

/** Port de sortie : historique des scans appliqués. */
public interface ScanRepository {

    boolean existsByEventId(UUID eventId);

    void save(UUID shipmentId, ScanEvent event);
}
