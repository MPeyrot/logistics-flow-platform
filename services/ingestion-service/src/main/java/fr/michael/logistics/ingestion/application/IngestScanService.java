package fr.michael.logistics.ingestion.application;

import fr.michael.logistics.ingestion.domain.ScanEvent;
import fr.michael.logistics.ingestion.domain.ScanType;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class IngestScanService {

    private final ScanPublisher publisher;
    private final Clock clock;

    public IngestScanService(ScanPublisher publisher, Clock clock) {
        this.publisher = publisher;
        this.clock = clock;
    }

    /**
     * Crée l'événement et le publie. Si l'appelant fournit un {@code eventId}, il est conservé :
     * un scanneur qui rejoue la même requête produit le même événement, dédoublonné par le consommateur.
     */
    public UUID ingest(UUID eventId, String shipmentReference, ScanType type, String site, Instant occurredAt) {
        ScanEvent event = new ScanEvent(
                eventId != null ? eventId : UUID.randomUUID(),
                shipmentReference,
                type,
                site,
                occurredAt != null ? occurredAt : clock.instant());
        publisher.publish(event);
        return event.eventId();
    }
}
