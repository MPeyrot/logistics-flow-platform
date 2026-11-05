package fr.michael.logistics.shipment.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

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
        Shipment outForDelivery = atSite.applyScan(scan(ScanType.OUT_FOR_DELIVERY));
        Shipment delivered = outForDelivery.applyScan(scan(ScanType.DELIVERED));

        assertThat(pickedUp.status()).isEqualTo(ShipmentStatus.IN_TRANSIT);
        assertThat(atSite.status()).isEqualTo(ShipmentStatus.IN_TRANSIT);
        assertThat(outForDelivery.status()).isEqualTo(ShipmentStatus.OUT_FOR_DELIVERY);
        assertThat(delivered.status()).isEqualTo(ShipmentStatus.DELIVERED);
    }

    @Test
    void statusesAndScanTypesAreThoseOfTheSpec() {
        assertThat(ShipmentStatus.values()).containsExactly(
                ShipmentStatus.CREATED, ShipmentStatus.IN_TRANSIT, ShipmentStatus.OUT_FOR_DELIVERY,
                ShipmentStatus.DELIVERED, ShipmentStatus.DELIVERY_FAILED);
        assertThat(ScanType.values()).containsExactly(
                ScanType.PICKED_UP, ScanType.ARRIVED_AT_SITE, ScanType.OUT_FOR_DELIVERY,
                ScanType.DELIVERED, ScanType.DELIVERY_FAILED);
    }

    /** Table des transitions permises, recopiée de la spec #33. */
    static Stream<Arguments> allowedTransitions() {
        return Stream.of(
                Arguments.of(ShipmentStatus.CREATED, ScanType.PICKED_UP, ShipmentStatus.IN_TRANSIT),
                Arguments.of(ShipmentStatus.IN_TRANSIT, ScanType.ARRIVED_AT_SITE, ShipmentStatus.IN_TRANSIT),
                Arguments.of(ShipmentStatus.IN_TRANSIT, ScanType.OUT_FOR_DELIVERY, ShipmentStatus.OUT_FOR_DELIVERY),
                Arguments.of(ShipmentStatus.OUT_FOR_DELIVERY, ScanType.DELIVERED, ShipmentStatus.DELIVERED),
                Arguments.of(ShipmentStatus.OUT_FOR_DELIVERY, ScanType.DELIVERY_FAILED, ShipmentStatus.DELIVERY_FAILED),
                Arguments.of(ShipmentStatus.DELIVERY_FAILED, ScanType.ARRIVED_AT_SITE, ShipmentStatus.IN_TRANSIT));
    }

    @ParameterizedTest(name = "{0} + {1} -> {2}")
    @MethodSource("allowedTransitions")
    void appliesAnAllowedTransition(ShipmentStatus from, ScanType scan, ShipmentStatus to) {
        Shipment shipment = inStatus(from);

        Shipment next = shipment.applyScan(scan(scan));

        assertThat(next.status()).isEqualTo(to);
        assertThat(next).usingRecursiveComparison().ignoringFields("status").isEqualTo(shipment);
    }

    /** Les 19 autres combinaisons statut × scan, dont IN_TRANSIT + DELIVERED et tout scan après DELIVERED. */
    static Stream<Arguments> refusedTransitions() {
        Set<List<Object>> allowed = allowedTransitions()
                .map(a -> List.of(a.get()[0], a.get()[1]))
                .collect(Collectors.toSet());
        return Arrays.stream(ShipmentStatus.values())
                .flatMap(status -> Arrays.stream(ScanType.values()).map(type -> List.<Object>of(status, type)))
                .filter(pair -> !allowed.contains(pair))
                .map(pair -> Arguments.of(pair.get(0), pair.get(1)));
    }

    @Test
    void refusesNineteenCombinations() {
        assertThat(refusedTransitions()).hasSize(19);
    }

    @ParameterizedTest(name = "{0} + {1} refused")
    @MethodSource("refusedTransitions")
    void refusesAnyOtherTransition(ShipmentStatus from, ScanType scan) {
        Shipment shipment = inStatus(from);

        assertThatThrownBy(() -> shipment.applyScan(scan(scan)))
                .isInstanceOf(InvalidScanException.class)
                .hasMessageContaining(scan.name())
                .hasMessageContaining(from.name());
    }

    private static Shipment inStatus(ShipmentStatus status) {
        return new Shipment(UUID.randomUUID(), "REF-1", "Nantes", "Lyon", status, NOW);
    }

    private static ScanEvent scan(ScanType type) {
        return new ScanEvent(UUID.randomUUID(), "REF-1", type, "Nantes", NOW);
    }
}
