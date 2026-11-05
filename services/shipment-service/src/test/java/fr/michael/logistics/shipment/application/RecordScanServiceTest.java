package fr.michael.logistics.shipment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.michael.logistics.shipment.domain.Anomaly;
import fr.michael.logistics.shipment.domain.AnomalyType;
import fr.michael.logistics.shipment.domain.InvalidScanException;
import fr.michael.logistics.shipment.domain.ScanEvent;
import fr.michael.logistics.shipment.domain.ScanType;
import fr.michael.logistics.shipment.domain.Shipment;
import fr.michael.logistics.shipment.domain.ShipmentStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RecordScanServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-31T10:00:00Z");

    private final InMemoryShipments shipments = new InMemoryShipments();
    private final InMemoryScans scans = new InMemoryScans();
    private final InMemoryAnomalies anomalies = new InMemoryAnomalies();
    private final RecordScanService service =
            new RecordScanService(shipments, scans, anomalies, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void appliesScanAndStoresIt() {
        shipments.save(Shipment.create("REF-1", "Nantes", "Lyon", NOW.plusSeconds(86_400), NOW));

        service.record(scan(UUID.randomUUID(), "REF-1", ScanType.PICKED_UP));

        assertThat(shipments.findByReference("REF-1")).get()
                .extracting(Shipment::status).isEqualTo(ShipmentStatus.IN_TRANSIT);
        assertThat(scans.saved).hasSize(1);
    }

    @Test
    void ignoresAnEventAlreadyProcessed() {
        shipments.save(Shipment.create("REF-1", "Nantes", "Lyon", NOW.plusSeconds(86_400), NOW));
        UUID eventId = UUID.randomUUID();

        service.record(scan(eventId, "REF-1", ScanType.PICKED_UP));
        service.record(scan(eventId, "REF-1", ScanType.PICKED_UP));

        assertThat(scans.saved).hasSize(1);
        assertThat(shipments.findByReference("REF-1")).get()
                .extracting(Shipment::status).isEqualTo(ShipmentStatus.IN_TRANSIT);
    }

    @Test
    void failsForUnknownShipment() {
        assertThatThrownBy(() -> service.record(scan(UUID.randomUUID(), "NOPE", ScanType.PICKED_UP)))
                .isInstanceOf(ShipmentNotFoundException.class);
        assertThat(scans.saved).isEmpty();
    }

    @Test
    void failsOnForbiddenTransitionWithoutStoringTheScan() {
        shipments.save(Shipment.create("REF-1", "Nantes", "Lyon", NOW.plusSeconds(86_400), NOW));

        assertThatThrownBy(() -> service.record(scan(UUID.randomUUID(), "REF-1", ScanType.DELIVERED)))
                .isInstanceOf(InvalidScanException.class);
        assertThat(scans.saved).isEmpty();
    }

    @Test
    void appliedScanResolvesTheOpenStalledAnomalyButNotTheLateOne() {
        Shipment shipment = shipments.save(Shipment.create("REF-1", "Nantes", "Lyon", NOW.plusSeconds(86_400), NOW));
        anomalies.openIfAbsent(Anomaly.open(shipment.id(), AnomalyType.STALLED, NOW.minusSeconds(60)));
        anomalies.openIfAbsent(Anomaly.open(shipment.id(), AnomalyType.LATE, NOW.minusSeconds(60)));

        service.record(scan(UUID.randomUUID(), "REF-1", ScanType.PICKED_UP));

        assertThat(anomalies.findOpen()).extracting(Anomaly::type).containsExactly(AnomalyType.LATE);
        assertThat(anomalies.resolved).singleElement()
                .satisfies(a -> assertThat(a.resolvedAt()).isEqualTo(NOW));
    }

    @Test
    void deliveryResolvesTheOpenLateAnomaly() {
        Shipment shipment = shipments.save(new Shipment(UUID.randomUUID(), "REF-1", "Nantes", "Lyon",
                ShipmentStatus.OUT_FOR_DELIVERY, NOW.minusSeconds(3_600), NOW.minusSeconds(60)));
        anomalies.openIfAbsent(Anomaly.open(shipment.id(), AnomalyType.LATE, NOW.minusSeconds(30)));

        service.record(scan(UUID.randomUUID(), "REF-1", ScanType.DELIVERED));

        assertThat(anomalies.findOpen()).isEmpty();
    }

    @Test
    void refusedScanResolvesNothing() {
        Shipment shipment = shipments.save(Shipment.create("REF-1", "Nantes", "Lyon", NOW.plusSeconds(86_400), NOW));
        anomalies.openIfAbsent(Anomaly.open(shipment.id(), AnomalyType.STALLED, NOW.minusSeconds(60)));

        assertThatThrownBy(() -> service.record(scan(UUID.randomUUID(), "REF-1", ScanType.DELIVERED)))
                .isInstanceOf(InvalidScanException.class);

        assertThat(anomalies.findOpen()).extracting(Anomaly::type).containsExactly(AnomalyType.STALLED);
    }

    private static ScanEvent scan(UUID eventId, String reference, ScanType type) {
        return new ScanEvent(eventId, reference, type, "Nantes", NOW);
    }

    private static class InMemoryShipments implements ShipmentRepository {
        private final Map<String, Shipment> byReference = new HashMap<>();

        @Override
        public Shipment save(Shipment shipment) {
            byReference.put(shipment.reference(), shipment);
            return shipment;
        }

        @Override
        public List<Shipment> findAll() {
            return List.copyOf(byReference.values());
        }

        @Override
        public Optional<Shipment> findByReference(String reference) {
            return Optional.ofNullable(byReference.get(reference));
        }

        @Override
        public Optional<Shipment> lockByReference(String reference) {
            return findByReference(reference);
        }

        @Override
        public Optional<Shipment> lockById(UUID id) {
            return byReference.values().stream().filter(s -> s.id().equals(id)).findFirst();
        }

        @Override
        public List<ShipmentActivity> findUndeliveredActivity() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean existsByReference(String reference) {
            return byReference.containsKey(reference);
        }
    }

    private static class InMemoryScans implements ScanRepository {
        private final List<ScanEvent> saved = new ArrayList<>();

        @Override
        public boolean existsByEventId(UUID eventId) {
            return saved.stream().anyMatch(e -> e.eventId().equals(eventId));
        }

        @Override
        public void save(UUID shipmentId, ScanEvent event) {
            saved.add(event);
        }

        @Override
        public Optional<Instant> lastOccurredAt(UUID shipmentId) {
            throw new UnsupportedOperationException();
        }
    }

    private static class InMemoryAnomalies implements AnomalyRepository {
        private final List<Anomaly> all = new ArrayList<>();
        private final List<Anomaly> resolved = new ArrayList<>();

        @Override
        public boolean openIfAbsent(Anomaly anomaly) {
            boolean alreadyOpen = all.stream().anyMatch(a -> a.isOpen()
                    && a.shipmentId().equals(anomaly.shipmentId()) && a.type() == anomaly.type());
            if (!alreadyOpen) {
                all.add(anomaly);
            }
            return !alreadyOpen;
        }

        @Override
        public void resolveOpen(UUID shipmentId, AnomalyType type, Instant resolvedAt) {
            all.replaceAll(a -> a.isOpen() && a.shipmentId().equals(shipmentId) && a.type() == type
                    ? resolve(a, resolvedAt) : a);
        }

        private Anomaly resolve(Anomaly open, Instant resolvedAt) {
            Anomaly closed = new Anomaly(open.id(), open.shipmentId(), open.type(), open.detectedAt(), resolvedAt);
            resolved.add(closed);
            return closed;
        }

        @Override
        public List<Anomaly> findOpen() {
            return all.stream().filter(Anomaly::isOpen).toList();
        }
    }
}
