package fr.michael.logistics.ingestion.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Scan reçu d'un terminal. {@code reason} : motif facultatif, seulement sur un échec de livraison. */
public record ScanEvent(
        UUID eventId,
        String shipmentReference,
        ScanType type,
        String site,
        Instant occurredAt,
        String reason) {

    public static final int REASON_MAX_LENGTH = 200;

    public ScanEvent {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(occurredAt, "occurredAt");
        requireText(shipmentReference, "shipmentReference");
        requireText(site, "site");
        if (reason != null) {
            if (type != ScanType.DELIVERY_FAILED) {
                throw new IllegalArgumentException("reason is only allowed with DELIVERY_FAILED");
            }
            requireText(reason, "reason");
            if (reason.length() > REASON_MAX_LENGTH) {
                throw new IllegalArgumentException("reason must not exceed " + REASON_MAX_LENGTH + " characters");
            }
        }
    }

    /** Scan sans motif. */
    public ScanEvent(UUID eventId, String shipmentReference, ScanType type, String site, Instant occurredAt) {
        this(eventId, shipmentReference, type, site, occurredAt, null);
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
