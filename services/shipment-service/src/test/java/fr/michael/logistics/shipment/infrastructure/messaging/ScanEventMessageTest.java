package fr.michael.logistics.shipment.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Le message Kafka est une donnée externe : Bean Validation le contrôle avant le domaine. */
class ScanEventMessageTest {

    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();
    private static final Instant NOW = Instant.parse("2026-10-31T10:00:00Z");

    @Test
    void acceptsAMessageWithinTheContract() {
        assertThat(VALIDATOR.validate(message("REF-1", "PICKED_UP", "Nantes", NOW, null))).isEmpty();
        assertThat(VALIDATOR.validate(message("REF-1", "DELIVERY_FAILED", "Nantes", NOW, "Absent"))).isEmpty();
    }

    @Test
    void rejectsMissingFieldsAndValuesOutsideTheContract() {
        assertThat(VALIDATOR.validate(new ScanEventMessage(null, "REF-1", "PICKED_UP", "Nantes", NOW, null))).isNotEmpty();
        assertThat(VALIDATOR.validate(message(" ", "PICKED_UP", "Nantes", NOW, null))).isNotEmpty();
        assertThat(VALIDATOR.validate(message("R".repeat(51), "PICKED_UP", "Nantes", NOW, null))).isNotEmpty();
        assertThat(VALIDATOR.validate(message("REF-1", null, "Nantes", NOW, null))).isNotEmpty();
        assertThat(VALIDATOR.validate(message("REF-1", "PICKED_UP", "N".repeat(101), NOW, null))).isNotEmpty();
        assertThat(VALIDATOR.validate(message("REF-1", "PICKED_UP", "Nantes", null, null))).isNotEmpty();
        assertThat(VALIDATOR.validate(message("REF-1", "DELIVERY_FAILED", "Nantes", NOW, " "))).isNotEmpty();
        assertThat(VALIDATOR.validate(message("REF-1", "DELIVERY_FAILED", "Nantes", NOW, "x".repeat(201)))).isNotEmpty();
    }

    @Test
    void countsReasonLengthInCodePoints() {
        assertThat(VALIDATOR.validate(
                message("REF-1", "DELIVERY_FAILED", "Nantes", NOW, "😀".repeat(200)))).isEmpty();
    }

    private static ScanEventMessage message(String ref, String type, String site, Instant at, String reason) {
        return new ScanEventMessage(UUID.randomUUID(), ref, type, site, at, reason);
    }
}
