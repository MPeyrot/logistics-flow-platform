package fr.michael.logistics.shipment.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
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
}
