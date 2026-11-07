package fr.michael.logistics.ingestion.infrastructure.batch;

import fr.michael.logistics.ingestion.domain.ScanEvent;
import fr.michael.logistics.ingestion.domain.ScanType;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

/**
 * Ligne CSV convertie, avec les mêmes contraintes que {@code ScanRequest} ({@code occurredAt} obligatoire en plus).
 * À garder alignée sur {@code ScanRequest}.
 */
record ScanRowDraft(
        UUID eventId,
        @NotBlank @Pattern(regexp = ScanEvent.NOT_BLANK_REGEX, message = "must not be blank") @Size(max = 50) String shipmentReference,
        @NotNull ScanType type,
        @NotBlank @Pattern(regexp = ScanEvent.NOT_BLANK_REGEX, message = "must not be blank") @Size(max = 100) String site,
        @NotNull Instant occurredAt,
        @Pattern(regexp = ScanEvent.NOT_BLANK_REGEX, message = "must not be blank") String reason) {

    /** Propriété {@code reasonShortEnough} des erreurs : 200 points de code, comme le contrat. */
    @AssertTrue(message = "must not exceed " + ScanEvent.REASON_MAX_LENGTH + " characters")
    boolean isReasonShortEnough() {
        return reason == null || reason.codePointCount(0, reason.length()) <= ScanEvent.REASON_MAX_LENGTH;
    }

    /** Propriété {@code reasonAllowed} des erreurs. */
    @AssertTrue(message = "reason is only allowed with DELIVERY_FAILED")
    boolean isReasonAllowed() {
        return reason == null || type == null || type == ScanType.DELIVERY_FAILED;
    }
}
