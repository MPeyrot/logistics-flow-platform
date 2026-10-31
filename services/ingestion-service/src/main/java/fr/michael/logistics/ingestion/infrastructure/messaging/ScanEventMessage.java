package fr.michael.logistics.ingestion.infrastructure.messaging;

import fr.michael.logistics.ingestion.domain.ScanEvent;
import java.time.Instant;
import java.util.UUID;

/** Forme JSON publiée sur Kafka : contracts/scan-event.v1.schema.json. */
record ScanEventMessage(
        UUID eventId,
        String shipmentReference,
        String type,
        String site,
        Instant occurredAt) {

    static ScanEventMessage from(ScanEvent event) {
        return new ScanEventMessage(
                event.eventId(), event.shipmentReference(), event.type().name(), event.site(), event.occurredAt());
    }
}
