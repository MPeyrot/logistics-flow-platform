package fr.michael.logistics.shipment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.michael.logistics.shipment.domain.InvalidScanException;
import fr.michael.logistics.shipment.domain.ScanEvent;
import fr.michael.logistics.shipment.domain.ScanType;
import fr.michael.logistics.shipment.domain.Shipment;
import fr.michael.logistics.shipment.domain.ShipmentStatus;
import java.time.Instant;
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
    private final RecordScanService service = new RecordScanService(shipments, scans);

    @Test
    void appliesScanAndStoresIt() {
        shipments.save(Shipment.create("REF-1", "Nantes", "Lyon", NOW));

        service.record(scan(UUID.randomUUID(), "REF-1", ScanType.PICKED_UP));

        assertThat(shipments.findByReference("REF-1")).get()
                .extracting(Shipment::status).isEqualTo(ShipmentStatus.IN_TRANSIT);
        assertThat(scans.saved).hasSize(1);
    }

    @Test
    void ignoresAnEventAlreadyProcessed() {
        shipments.save(Shipment.create("REF-1", "Nantes", "Lyon", NOW));
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
        shipments.save(Shipment.create("REF-1", "Nantes", "Lyon", NOW));

        assertThatThrownBy(() -> service.record(scan(UUID.randomUUID(), "REF-1", ScanType.DELIVERED)))
                .isInstanceOf(InvalidScanException.class);
        assertThat(scans.saved).isEmpty();
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
    }
}
