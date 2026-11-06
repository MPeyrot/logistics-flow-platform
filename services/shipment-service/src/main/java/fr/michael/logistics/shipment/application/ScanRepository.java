package fr.michael.logistics.shipment.application;

import fr.michael.logistics.shipment.domain.ScanEvent;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Port de sortie : historique des scans appliqués. */
public interface ScanRepository {

    boolean existsByEventId(UUID eventId);

    /** @param receivedAt heure à laquelle ce service a reçu le scan (pas sa date métier {@code occurredAt}) */
    void save(UUID shipmentId, ScanEvent event, Instant receivedAt);

    /** Heure de réception la plus récente des scans de l'expédition ; vide si elle n'a jamais été scannée. */
    Optional<Instant> lastReceivedAt(UUID shipmentId);
}
