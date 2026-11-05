package fr.michael.logistics.shipment.application;

import fr.michael.logistics.shipment.domain.ScanEvent;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Port de sortie : historique des scans appliqués. */
public interface ScanRepository {

    boolean existsByEventId(UUID eventId);

    void save(UUID shipmentId, ScanEvent event);

    /** {@code occurredAt} le plus récent des scans de l'expédition ; vide si elle n'a jamais été scannée. */
    Optional<Instant> lastOccurredAt(UUID shipmentId);
}
