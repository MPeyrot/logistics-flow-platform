package fr.michael.logistics.ingestion.infrastructure.web;

import fr.michael.logistics.ingestion.domain.ScanType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

record ScanRequest(
        UUID eventId,
        @NotBlank @Size(max = 50) String shipmentReference,
        @NotNull ScanType type,
        @NotBlank @Size(max = 100) String site,
        Instant occurredAt) {
}
