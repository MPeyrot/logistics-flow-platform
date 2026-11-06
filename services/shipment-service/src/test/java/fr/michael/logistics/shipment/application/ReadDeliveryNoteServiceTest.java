package fr.michael.logistics.shipment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.michael.logistics.shipment.domain.Shipment;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Quota d'une extraction par heure : seuls les appels qu'ai-service a pu facturer le consomment. */
class ReadDeliveryNoteServiceTest {

    private final Deque<RuntimeException> readerFailures = new ArrayDeque<>();
    private int readerCalls;
    private final DeliveryNoteReader reader = (content, filename) -> {
        readerCalls++;
        throw readerFailures.removeFirst();
    };
    private final ReadDeliveryNoteService service = new ReadDeliveryNoteService(reader, new NoShipments(), new NoAnomalies(),
            new ExtractionRateLimiter(1, Duration.ofHours(1),
                    Clock.fixed(Instant.parse("2026-11-03T10:00:00Z"), ZoneOffset.UTC)));

    @Test
    void unsupportedDocumentGivesTheQuotaBack() {
        readerFailures.add(new UnsupportedDocumentException("not a document"));
        readerFailures.add(new UnreadableDeliveryNoteException("no delivery note"));

        assertThatThrownBy(this::read).isInstanceOf(UnsupportedDocumentException.class);
        assertThatThrownBy(this::read).isInstanceOf(UnreadableDeliveryNoteException.class);
        assertThat(readerCalls).isEqualTo(2);
    }

    @Test
    void rateLimitedAiServiceGivesTheQuotaBack() {
        readerFailures.add(new ExtractionRateLimitedException("ai-service rate limited", 30L));
        readerFailures.add(new UnreadableDeliveryNoteException("no delivery note"));

        assertThatThrownBy(this::read).isInstanceOf(ExtractionRateLimitedException.class);
        assertThatThrownBy(this::read).isInstanceOf(UnreadableDeliveryNoteException.class);
        assertThat(readerCalls).isEqualTo(2);
    }

    /** 422 et 502 : un appel Claude a pu être facturé, le quota reste pris. */
    @Test
    void unreadableNoteKeepsTheQuota() {
        readerFailures.add(new UnreadableDeliveryNoteException("no delivery note"));
        assertThatThrownBy(this::read).isInstanceOf(UnreadableDeliveryNoteException.class);
        assertThatThrownBy(this::read).isInstanceOf(ExtractionRateLimitedException.class);

        assertThat(readerCalls).isEqualTo(1);
    }

    @Test
    void unavailableServiceKeepsTheQuota() {
        readerFailures.add(new ExtractionServiceUnavailableException("ai-service answered 500"));
        assertThatThrownBy(this::read).isInstanceOf(ExtractionServiceUnavailableException.class);
        assertThatThrownBy(this::read).isInstanceOf(ExtractionRateLimitedException.class);

        assertThat(readerCalls).isEqualTo(1);
    }

    /** Connexion refusée ou hôte injoignable : aucune requête n'est partie, donc aucun appel Claude (#29). */
    @Test
    void unreachableServiceGivesTheQuotaBack() {
        readerFailures.add(new ExtractionServiceUnreachableException("connection refused", new java.net.ConnectException()));
        readerFailures.add(new UnreadableDeliveryNoteException("no delivery note"));

        assertThatThrownBy(this::read).isInstanceOf(ExtractionServiceUnreachableException.class);
        assertThatThrownBy(this::read).isInstanceOf(UnreadableDeliveryNoteException.class);
        assertThat(readerCalls).isEqualTo(2);
    }

    private void read() {
        service.read("alice", new byte[] {1}, "note.pdf");
    }

    private static final class NoAnomalies implements AnomalyRepository {
        @Override
        public boolean openIfAbsent(fr.michael.logistics.shipment.domain.Anomaly anomaly) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void resolveOpen(UUID shipmentId, fr.michael.logistics.shipment.domain.AnomalyType type, Instant resolvedAt) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<fr.michael.logistics.shipment.domain.Anomaly> findOpen() {
            return List.of();
        }

        @Override
        public List<fr.michael.logistics.shipment.domain.Anomaly> findOpenByShipmentId(UUID shipmentId) {
            return List.of();
        }

        @Override
        public List<AnomalyView> findViews(Boolean open) {
            return List.of();
        }
    }

    private static final class NoShipments implements ShipmentRepository {
        @Override
        public Shipment save(Shipment shipment) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<Shipment> findAll() {
            return List.of();
        }

        @Override
        public Optional<Shipment> findByReference(String reference) {
            return Optional.empty();
        }

        @Override
        public Optional<Shipment> lockByReference(String reference) {
            return Optional.empty();
        }

        @Override
        public Optional<Shipment> lockById(UUID id) {
            return Optional.empty();
        }

        @Override
        public List<UUID> findAnomalyCandidateIds(Instant now, Instant stalledBefore) {
            return List.of();
        }

        @Override
        public boolean existsByReference(String reference) {
            return false;
        }
    }
}
