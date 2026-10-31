package fr.michael.logistics.ingestion.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ScanEvent(
        UUID eventId,
        String shipmentReference,
        ScanType type,
        String site,
        Instant occurredAt) {

    public ScanEvent {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(occurredAt, "occurredAt");
        requireText(shipmentReference, "shipmentReference");
        requireText(site, "site");
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
