package fr.michael.logistics.ingestion.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DeterministicEventIdTest {

    private static final Instant AT = Instant.parse("2026-11-07T10:00:00Z");

    @Test
    void sameScanGivesSameId() {
        assertThat(DeterministicEventId.of("REF-1", ScanType.PICKED_UP, "Nantes", AT))
                .isEqualTo(DeterministicEventId.of("REF-1", ScanType.PICKED_UP, "Nantes", AT));
    }

    /** Valeur calculée à part : MD5 de {@code REF-1|PICKED_UP|Nantes|2026-11-07T10:00:00Z}, bits de version 3. */
    @Test
    void idIsTheNameBasedUuidOfTheFourFields() {
        assertThat(DeterministicEventId.of("REF-1", ScanType.PICKED_UP, "Nantes", AT))
                .isEqualTo(UUID.fromString("8726ae49-7309-3b06-8f4c-6655cfb85655"));
    }

    @Test
    void anyFieldChangesTheId() {
        UUID base = DeterministicEventId.of("REF-1", ScanType.PICKED_UP, "Nantes", AT);
        assertThat(DeterministicEventId.of("REF-2", ScanType.PICKED_UP, "Nantes", AT)).isNotEqualTo(base);
        assertThat(DeterministicEventId.of("REF-1", ScanType.DELIVERED, "Nantes", AT)).isNotEqualTo(base);
        assertThat(DeterministicEventId.of("REF-1", ScanType.PICKED_UP, "Lyon", AT)).isNotEqualTo(base);
        assertThat(DeterministicEventId.of("REF-1", ScanType.PICKED_UP, "Nantes", AT.plusMillis(1)))
                .isNotEqualTo(base);
    }
}
