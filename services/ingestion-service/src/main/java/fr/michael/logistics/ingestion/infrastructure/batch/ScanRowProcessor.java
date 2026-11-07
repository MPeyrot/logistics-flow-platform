package fr.michael.logistics.ingestion.infrastructure.batch;

import fr.michael.logistics.ingestion.domain.DeterministicEventId;
import fr.michael.logistics.ingestion.domain.ScanEvent;
import fr.michael.logistics.ingestion.domain.ScanType;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.springframework.batch.infrastructure.item.ItemProcessor;

/**
 * Valide une ligne par Bean Validation ({@link ScanRowDraft}, les contraintes de {@code POST /api/scans}) et exige que
 * son site soit celui du SCANNER. Les erreurs sont au format {@code champ: message}, jointes par {@code "; "}.
 */
class ScanRowProcessor implements ItemProcessor<ScanRow, ScanEvent> {

    private static final int UUID_LENGTH = 36;

    private final String site;
    private final ImportProgress progress;
    private final Validator validator;

    ScanRowProcessor(String site, ImportProgress progress, Validator validator) {
        this.validator = validator;
        this.site = site;
        this.progress = progress;
    }

    @Override
    public ScanEvent process(ScanRow row) {
        if (row.isBlank()) {
            return null;
        }
        progress.lineSeen();
        if (row.structureError() != null) {
            throw new RowRejected(row.line(), row.structureError());
        }
        List<String> errors = new ArrayList<>();
        ScanType type = null;
        String typeText = text(row, "type");
        if (typeText != null) {
            try {
                type = ScanType.valueOf(typeText);
            } catch (IllegalArgumentException e) {
                errors.add("type: Invalid value for type");
            }
        }
        Instant occurredAt = null;
        String occurredAtText = text(row, "occurredAt");
        if (occurredAtText != null) {
            try {
                occurredAt = OffsetDateTime.parse(occurredAtText).toInstant();
            } catch (DateTimeParseException e) {
                errors.add("occurredAt: Invalid value for occurredAt");
            }
        }
        UUID eventId = null;
        String eventIdText = text(row, "eventId");
        if (eventIdText != null) {
            try {
                // forme canonique seulement : UUID.fromString accepte « 1-1-1-1-1 », que Jackson refuse
                if (eventIdText.length() != UUID_LENGTH) {
                    throw new IllegalArgumentException("not a canonical UUID");
                }
                eventId = UUID.fromString(eventIdText);
            } catch (IllegalArgumentException e) {
                errors.add("eventId: Invalid value for eventId");
            }
        }
        ScanRowDraft draft = new ScanRowDraft(eventId, text(row, "shipmentReference"), type, text(row, "site"),
                occurredAt, text(row, "reason"));
        for (ConstraintViolation<ScanRowDraft> violation : validator.validate(draft)) {
            String field = violation.getPropertyPath().toString();
            // une valeur déjà signalée illisible n'est pas signalée une seconde fois comme absente
            if (errors.stream().noneMatch(error -> error.startsWith(field + ":"))) {
                errors.add(field + ": " + violation.getMessage());
            }
        }
        if (draft.site() != null && !draft.site().equals(site) && errors.stream().noneMatch(e -> e.startsWith("site:"))) {
            errors.add("site: must match the token site");
        }
        if (!errors.isEmpty()) {
            Collections.sort(errors);
            throw new RowRejected(row.line(), String.join("; ", errors));
        }
        UUID id = draft.eventId() != null
                ? draft.eventId()
                : DeterministicEventId.of(draft.shipmentReference(), type, draft.site(), occurredAt);
        try {
            return new ScanEvent(id, draft.shipmentReference(), type, draft.site(), occurredAt, draft.reason());
        } catch (IllegalArgumentException e) {
            // filet : une règle du domaine que la validation ne couvrirait pas rejette la ligne, pas le job
            throw new RowRejected(row.line(), e.getMessage());
        }
    }

    /** Cellule absente ou vide : {@code null}. */
    private static String text(ScanRow row, String column) {
        String value = row.values().get(column);
        return value == null || value.isEmpty() ? null : value;
    }
}
