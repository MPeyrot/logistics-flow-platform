package fr.michael.logistics.shipment.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ShipmentTest {

    private static final Instant NOW = Instant.parse("2026-10-31T10:00:00Z");

    @Test
    void newShipmentStartsAsCreated() {
        Shipment shipment = Shipment.create("REF-1", "Nantes", "Lyon", NOW);

        assertThat(shipment.status()).isEqualTo(ShipmentStatus.CREATED);
        assertThat(shipment.createdAt()).isEqualTo(NOW);
    }

    @Test
    void rejectsSameOriginAndDestination() {
        assertThatThrownBy(() -> Shipment.create("REF-1", "Nantes", "Nantes", NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsBlankReference() {
        assertThatThrownBy(() -> Shipment.create(" ", "Nantes", "Lyon", NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void followsTheNominalScanSequence() {
        Shipment shipment = Shipment.create("REF-1", "Nantes", "Lyon", NOW);

        Shipment pickedUp = shipment.applyScan(scan(ScanType.PICKED_UP));
        Shipment atSite = pickedUp.applyScan(scan(ScanType.ARRIVED_AT_SITE));
        Shipment delivered = atSite.applyScan(scan(ScanType.DELIVERED));

        assertThat(pickedUp.status()).isEqualTo(ShipmentStatus.IN_TRANSIT);
        assertThat(atSite.status()).isEqualTo(ShipmentStatus.IN_TRANSIT);
        assertThat(delivered.status()).isEqualTo(ShipmentStatus.DELIVERED);
    }

    @Test
    void rejectsDeliveryBeforePickup() {
        Shipment shipment = Shipment.create("REF-1", "Nantes", "Lyon", NOW);

        assertThatThrownBy(() -> shipment.applyScan(scan(ScanType.DELIVERED)))
                .isInstanceOf(InvalidScanException.class);
    }

    @Test
    void rejectsAnyScanAfterDelivery() {
        Shipment delivered = Shipment.create("REF-1", "Nantes", "Lyon", NOW)
                .applyScan(scan(ScanType.PICKED_UP))
                .applyScan(scan(ScanType.DELIVERED));

        assertThatThrownBy(() -> delivered.applyScan(scan(ScanType.ARRIVED_AT_SITE)))
                .isInstanceOf(InvalidScanException.class);
    }

    private static ScanEvent scan(ScanType type) {
        return new ScanEvent(UUID.randomUUID(), "REF-1", type, "Nantes", NOW);
    }
}
