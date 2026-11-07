package fr.michael.logistics.ingestion.infrastructure.batch;

import fr.michael.logistics.ingestion.domain.DeterministicEventId;
import fr.michael.logistics.ingestion.domain.ScanEvent;
import fr.michael.logistics.ingestion.domain.ScanType;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.batch.infrastructure.item.ItemProcessor;

/**
 * Valide une ligne avec les règles de {@code POST /api/scans} ({@code occurredAt} obligatoire en plus) et exige que
 * son site soit celui du SCANNER. Les erreurs sont au format {@code champ: message}, jointes par {@code "; "}.
 */
class ScanRowProcessor implements ItemProcessor<ScanRow, ScanEvent> {

    private static final Pattern NOT_BLANK = Pattern.compile(ScanEvent.NOT_BLANK_REGEX);

    private final String site;
    private final ImportProgress progress;

    ScanRowProcessor(String site, ImportProgress progress) {
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
        String reference = text(row, "shipmentReference");
        if (!notBlank(reference)) {
            errors.add("shipmentReference: must not be blank");
        } else if (reference.length() > 50) {
            errors.add("shipmentReference: size must be between 0 and 50");
        }
        ScanType type = null;
        String typeText = text(row, "type");
        if (typeText == null) {
            errors.add("type: must not be null");
        } else {
            try {
                type = ScanType.valueOf(typeText);
            } catch (IllegalArgumentException e) {
                errors.add("type: Invalid value for type");
            }
        }
        String rowSite = text(row, "site");
        if (!notBlank(rowSite)) {
            errors.add("site: must not be blank");
        } else if (rowSite.length() > 100) {
            errors.add("site: size must be between 0 and 100");
        } else if (!rowSite.equals(site)) {
            errors.add("site: must match the token site");
        }
        Instant occurredAt = null;
        String occurredAtText = text(row, "occurredAt");
        if (occurredAtText == null) {
            errors.add("occurredAt: must not be null");
        } else {
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
                eventId = UUID.fromString(eventIdText);
            } catch (IllegalArgumentException e) {
                errors.add("eventId: Invalid value for eventId");
            }
        }
        String reason = text(row, "reason");
        if (reason != null) {
            if (!notBlank(reason)) {
                errors.add("reason: must not be blank");
            }
            if (reason.codePointCount(0, reason.length()) > ScanEvent.REASON_MAX_LENGTH) {
                errors.add("reasonShortEnough: must not exceed " + ScanEvent.REASON_MAX_LENGTH + " characters");
            }
            if (type != null && type != ScanType.DELIVERY_FAILED) {
                errors.add("reasonAllowed: reason is only allowed with DELIVERY_FAILED");
            }
        }
        if (!errors.isEmpty()) {
            throw new RowRejected(row.line(), String.join("; ", errors));
        }
        if (eventId == null) {
            eventId = DeterministicEventId.of(reference, type, rowSite, occurredAt);
        }
        return new ScanEvent(eventId, reference, type, rowSite, occurredAt, reason);
    }

    /** Cellule absente ou vide : {@code null}. */
    private static String text(ScanRow row, String column) {
        String value = row.values().get(column);
        return value == null || value.isEmpty() ? null : value;
    }

    private static boolean notBlank(String value) {
        return value != null && NOT_BLANK.matcher(value).matches();
    }
}
