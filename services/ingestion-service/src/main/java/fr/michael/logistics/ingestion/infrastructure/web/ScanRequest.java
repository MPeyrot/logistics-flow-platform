package fr.michael.logistics.ingestion.infrastructure.web;

import fr.michael.logistics.ingestion.domain.ScanEvent;
import fr.michael.logistics.ingestion.domain.ScanType;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

record ScanRequest(
        UUID eventId,
        @NotBlank @Size(max = 50) String shipmentReference,
        @NotNull ScanType type,
        @NotBlank @Size(max = 100) String site,
        Instant occurredAt,
        @Size(min = 1, max = ScanEvent.REASON_MAX_LENGTH)
        @Pattern(regexp = "(?s).*\\S.*", message = "must not be blank") String reason) {

    /** Propriété {@code reasonAllowed} des erreurs de validation. */
    @AssertTrue(message = "reason is only allowed with DELIVERY_FAILED")
    boolean isReasonAllowed() {
        return reason == null || type == ScanType.DELIVERY_FAILED;
    }
}
