package fr.michael.logistics.shipment.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class ScanEventTest {

    private static final Instant NOW = Instant.parse("2026-11-06T10:00:00Z");

    @Test
    void failedDeliveryCarriesItsReason() {
        assertThat(scan(ScanType.DELIVERY_FAILED, "Destinataire absent").reason()).isEqualTo("Destinataire absent");
    }

    @Test
    void reasonIsOptional() {
        assertThat(scan(ScanType.DELIVERY_FAILED, null).reason()).isNull();
    }

    @ParameterizedTest
    @EnumSource(value = ScanType.class, names = "DELIVERY_FAILED", mode = EnumSource.Mode.EXCLUDE)
    void rejectsReasonOnAnotherType(ScanType type) {
        assertThatThrownBy(() -> scan(type, "Destinataire absent"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "\u00a0", " \u3000\ufeff"})
    void rejectsBlankReason(String reason) {
        assertThatThrownBy(() -> scan(ScanType.DELIVERY_FAILED, reason))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void countsReasonLengthInCodePoints() {
        assertThat(scan(ScanType.DELIVERY_FAILED, "\uD83D\uDE00".repeat(200)).reason()).hasSize(400);
        assertThatThrownBy(() -> scan(ScanType.DELIVERY_FAILED, "\uD83D\uDE00".repeat(201)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void acceptsReasonOf200CharactersButNotMore() {
        assertThat(scan(ScanType.DELIVERY_FAILED, "x".repeat(200)).reason()).hasSize(200);
        assertThatThrownBy(() -> scan(ScanType.DELIVERY_FAILED, "x".repeat(201)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static ScanEvent scan(ScanType type, String reason) {
        return new ScanEvent(UUID.randomUUID(), "REF-1", type, "Nantes", NOW, reason);
    }
}
