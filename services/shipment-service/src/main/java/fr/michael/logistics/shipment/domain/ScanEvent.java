package fr.michael.logistics.shipment.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/** Scan d'une expédition. {@code reason} : motif facultatif, seulement sur un échec de livraison. */
public record ScanEvent(
        UUID eventId,
        String shipmentReference,
        ScanType type,
        String site,
        Instant occurredAt,
        String reason) {

    /** Longueur maximale de {@code reason}, en points de code (comme le {@code maxLength} du contrat et la colonne). */
    public static final int REASON_MAX_LENGTH = 200;

    /** Les blancs du {@code \S} du contrat (ECMAScript), espace insécable compris. */
    private static final String BLANKS =
            "\\t\\n\\u000B\\f\\r \\u00A0\\u1680\\u2000-\\u200A\\u2028\\u2029\\u202F\\u205F\\u3000\\uFEFF";

    /** Motif d'un texte contenant au moins un non-blanc, pour Bean Validation. */
    public static final String NOT_BLANK_REGEX = "(?s).*[^" + BLANKS + "].*";

    private static final Pattern NOT_BLANK = Pattern.compile(NOT_BLANK_REGEX);

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
            if (reason.codePointCount(0, reason.length()) > REASON_MAX_LENGTH) {
                throw new IllegalArgumentException("reason must not exceed " + REASON_MAX_LENGTH + " characters");
            }
        }
    }

    /** Scan sans motif. */
    public ScanEvent(UUID eventId, String shipmentReference, ScanType type, String site, Instant occurredAt) {
        this(eventId, shipmentReference, type, site, occurredAt, null);
    }

    private static void requireText(String value, String field) {
        if (value == null || !NOT_BLANK.matcher(value).matches()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
