package fr.michael.logistics.shipment.application;

import fr.michael.logistics.shipment.domain.ScanType;
import java.time.Instant;
import java.util.UUID;

/** Scan appliqué à une expédition, tel qu'enregistré : {@code receivedAt} est l'heure de réception par ce service. */
public record ScanRecord(
        UUID eventId, ScanType type, String site, Instant occurredAt, Instant receivedAt, String reason) {
}
