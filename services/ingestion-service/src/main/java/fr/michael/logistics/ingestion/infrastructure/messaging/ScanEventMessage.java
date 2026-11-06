package fr.michael.logistics.ingestion.infrastructure.messaging;

import com.fasterxml.jackson.annotation.JsonInclude;
import fr.michael.logistics.ingestion.domain.ScanEvent;
import java.time.Instant;
import java.util.UUID;

/** Forme JSON publiée sur Kafka : contracts/scan-event.v2.schema.json ({@code reason} omis s'il est absent). */
record ScanEventMessage(
        UUID eventId,
        String shipmentReference,
        String type,
        String site,
        Instant occurredAt,
        @JsonInclude(JsonInclude.Include.NON_NULL) String reason) {

    static ScanEventMessage from(ScanEvent event) {
        return new ScanEventMessage(event.eventId(), event.shipmentReference(), event.type().name(), event.site(),
                event.occurredAt(), event.reason());
    }
}
