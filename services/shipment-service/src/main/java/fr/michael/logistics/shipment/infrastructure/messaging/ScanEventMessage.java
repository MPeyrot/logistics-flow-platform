package fr.michael.logistics.shipment.infrastructure.messaging;

import fr.michael.logistics.shipment.domain.ScanEvent;
import fr.michael.logistics.shipment.domain.ScanType;
import java.time.Instant;
import java.util.UUID;

/** Forme JSON consommée : contracts/scan-event.v2.schema.json ({@code reason} absent d'un message v1). */
record ScanEventMessage(
        UUID eventId,
        String shipmentReference,
        String type,
        String site,
        Instant occurredAt,
        String reason) {

    ScanEvent toDomain() {
        return new ScanEvent(eventId, shipmentReference, ScanType.valueOf(type), site, occurredAt, reason);
    }
}
