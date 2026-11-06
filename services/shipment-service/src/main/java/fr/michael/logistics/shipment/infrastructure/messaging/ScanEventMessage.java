package fr.michael.logistics.shipment.infrastructure.messaging;

import fr.michael.logistics.shipment.domain.ScanEvent;
import fr.michael.logistics.shipment.domain.ScanType;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

/**
 * Forme JSON consommée : contracts/scan-event.v2.schema.json ({@code reason} absent d'un message v1).
 * Contrôlée par Bean Validation avant le domaine ; un échec rend le message illisible (DLT immédiat).
 */
record ScanEventMessage(
        @NotNull UUID eventId,
        @NotBlank @Size(max = 50) String shipmentReference,
        @NotNull String type,
        @NotBlank @Size(max = 100) String site,
        @NotNull Instant occurredAt,
        @Pattern(regexp = ScanEvent.NOT_BLANK_REGEX, message = "must not be blank") String reason) {

    /** Propriété {@code reasonShortEnough} : 200 points de code, comme le contrat. */
    @AssertTrue(message = "must not exceed " + ScanEvent.REASON_MAX_LENGTH + " characters")
    boolean isReasonShortEnough() {
        return reason == null || reason.codePointCount(0, reason.length()) <= ScanEvent.REASON_MAX_LENGTH;
    }

    ScanEvent toDomain() {
        return new ScanEvent(eventId, shipmentReference, ScanType.valueOf(type), site, occurredAt, reason);
    }
}
