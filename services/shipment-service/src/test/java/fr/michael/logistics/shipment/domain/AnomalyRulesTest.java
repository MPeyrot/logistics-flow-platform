package fr.michael.logistics.shipment.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Horloge fixe : {@code NOW}, seuil STALLED de 48 h. */
class AnomalyRulesTest {

    private static final Instant NOW = Instant.parse("2026-11-05T12:00:00Z");
    private static final Duration STALLED_AFTER = Duration.ofHours(48);

    @Test
    void createdShipmentWithoutScanIsStalledFrom48HoursAfterCreation() {
        Shipment justBefore = shipment(ShipmentStatus.CREATED, NOW.minus(Duration.ofHours(48)).plusSeconds(1), null);
        Shipment exactly = shipment(ShipmentStatus.CREATED, NOW.minus(Duration.ofHours(48)), null);

        assertThat(detect(justBefore, null)).isEmpty();
        assertThat(detect(exactly, null)).containsExactly(AnomalyType.STALLED);
    }

    @Test
    void stalledCountsFromTheLastScanWhenThereIsOne() {
        Shipment shipment = shipment(ShipmentStatus.IN_TRANSIT, NOW.minus(Duration.ofDays(10)), null);

        assertThat(detect(shipment, NOW.minus(Duration.ofHours(47)))).isEmpty();
        assertThat(detect(shipment, NOW.minus(Duration.ofHours(49)))).containsExactly(AnomalyType.STALLED);
    }

    @Test
    void undeliveredShipmentIsLateOnceItsExpectedDeliveryHasPassed() {
        Shipment due = shipment(ShipmentStatus.OUT_FOR_DELIVERY, NOW.minus(Duration.ofHours(5)), NOW);
        Shipment overdue = shipment(ShipmentStatus.DELIVERY_FAILED, NOW.minus(Duration.ofHours(5)), NOW.minusSeconds(1));

        assertThat(detect(due, NOW.minus(Duration.ofHours(1)))).isEmpty();
        assertThat(detect(overdue, NOW.minus(Duration.ofHours(1)))).containsExactly(AnomalyType.LATE);
    }

    @Test
    void shipmentWithoutExpectedDeliveryIsNeverLate() {
        Shipment legacy = shipment(ShipmentStatus.IN_TRANSIT, NOW.minus(Duration.ofDays(365)), null);

        assertThat(detect(legacy, NOW.minusSeconds(60))).isEmpty();
    }

    @Test
    void lateAndStalledCanBeDetectedTogether() {
        Shipment shipment = shipment(ShipmentStatus.CREATED, NOW.minus(Duration.ofDays(3)), NOW.minus(Duration.ofDays(1)));

        assertThat(detect(shipment, null)).containsExactlyInAnyOrder(AnomalyType.LATE, AnomalyType.STALLED);
    }

    @Test
    void deliveredShipmentIsNeverAnAnomaly() {
        Shipment delivered = shipment(ShipmentStatus.DELIVERED, NOW.minus(Duration.ofDays(30)), NOW.minus(Duration.ofDays(20)));

        assertThat(detect(delivered, NOW.minus(Duration.ofDays(25)))).isEmpty();
    }

    @Test
    void anyAppliedScanResolvesStalledAndDeliveryAlsoResolvesLate() {
        Shipment inTransit = shipment(ShipmentStatus.IN_TRANSIT, NOW, NOW.plusSeconds(60));
        Shipment failed = shipment(ShipmentStatus.DELIVERY_FAILED, NOW, NOW.plusSeconds(60));
        Shipment delivered = shipment(ShipmentStatus.DELIVERED, NOW, NOW.plusSeconds(60));

        assertThat(AnomalyRules.resolvedByScan(inTransit)).containsExactly(AnomalyType.STALLED);
        assertThat(AnomalyRules.resolvedByScan(failed)).containsExactly(AnomalyType.STALLED);
        assertThat(AnomalyRules.resolvedByScan(delivered))
                .containsExactlyInAnyOrder(AnomalyType.STALLED, AnomalyType.LATE);
    }

    private static java.util.Set<AnomalyType> detect(Shipment shipment, Instant lastScanReceivedAt) {
        return AnomalyRules.detect(shipment, lastScanReceivedAt, NOW, STALLED_AFTER);
    }

    private static Shipment shipment(ShipmentStatus status, Instant createdAt, Instant expectedDeliveryAt) {
        return new Shipment(UUID.randomUUID(), "REF-1", "Nantes", "Lyon", status, createdAt, expectedDeliveryAt);
    }
}
