package fr.michael.logistics.ingestion.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.michael.logistics.ingestion.domain.ScanEvent;
import fr.michael.logistics.ingestion.domain.ScanType;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class IngestScanServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-31T10:00:00Z");

    private final List<ScanEvent> published = new ArrayList<>();
    private final IngestScanService service =
            new IngestScanService(published::add, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void generatesEventIdAndTimestampWhenAbsent() {
        UUID id = service.ingest(null, "REF-1", ScanType.PICKED_UP, "Nantes", null, null);

        assertThat(published).singleElement().satisfies(event -> {
            assertThat(event.eventId()).isEqualTo(id);
            assertThat(event.occurredAt()).isEqualTo(NOW);
        });
    }

    @Test
    void keepsEventIdProvidedByCaller() {
        UUID id = UUID.randomUUID();

        assertThat(service.ingest(id, "REF-1", ScanType.DELIVERED, "Lyon", NOW, null)).isEqualTo(id);
    }

    @Test
    void publishesTheReasonOfAFailedDelivery() {
        service.ingest(null, "REF-1", ScanType.DELIVERY_FAILED, "Lyon", NOW, "Destinataire absent");

        assertThat(published).singleElement().extracting(ScanEvent::reason).isEqualTo("Destinataire absent");
    }

    @Test
    void rejectsReasonOnAnotherTypeWithoutPublishing() {
        assertThatThrownBy(() -> service.ingest(null, "REF-1", ScanType.DELIVERED, "Lyon", NOW, "Destinataire absent"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(published).isEmpty();
    }

    @Test
    void rejectsBlankReferenceWithoutPublishing() {
        assertThatThrownBy(() -> service.ingest(null, " ", ScanType.PICKED_UP, "Nantes", null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(published).isEmpty();
    }
}
