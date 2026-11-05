package fr.michael.logistics.shipment.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record Shipment(
        UUID id,
        String reference,
        String origin,
        String destination,
        ShipmentStatus status,
        Instant createdAt) {

    public Shipment {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
        requireText(reference, "reference");
        requireText(origin, "origin");
        requireText(destination, "destination");
        if (origin.equals(destination)) {
            throw new IllegalArgumentException("origin and destination must differ");
        }
    }

    public static Shipment create(String reference, String origin, String destination, Instant now) {
        return new Shipment(UUID.randomUUID(), reference, origin, destination, ShipmentStatus.CREATED, now);
    }

    /**
     * Applique un scan. Transitions permises (toute autre combinaison lève {@link InvalidScanException}) :
     * CREATED + PICKED_UP → IN_TRANSIT ; IN_TRANSIT + ARRIVED_AT_SITE → IN_TRANSIT ;
     * IN_TRANSIT + OUT_FOR_DELIVERY → OUT_FOR_DELIVERY ; OUT_FOR_DELIVERY + DELIVERED → DELIVERED ;
     * OUT_FOR_DELIVERY + DELIVERY_FAILED → DELIVERY_FAILED ; DELIVERY_FAILED + ARRIVED_AT_SITE → IN_TRANSIT.
     */
    public Shipment applyScan(ScanEvent scan) {
        ShipmentStatus next = switch (status) {
            case CREATED -> scan.type() == ScanType.PICKED_UP ? ShipmentStatus.IN_TRANSIT : null;
            case IN_TRANSIT -> switch (scan.type()) {
                case ARRIVED_AT_SITE -> ShipmentStatus.IN_TRANSIT;
                case OUT_FOR_DELIVERY -> ShipmentStatus.OUT_FOR_DELIVERY;
                default -> null;
            };
            case OUT_FOR_DELIVERY -> switch (scan.type()) {
                case DELIVERED -> ShipmentStatus.DELIVERED;
                case DELIVERY_FAILED -> ShipmentStatus.DELIVERY_FAILED;
                default -> null;
            };
            case DELIVERY_FAILED -> scan.type() == ScanType.ARRIVED_AT_SITE ? ShipmentStatus.IN_TRANSIT : null;
            case DELIVERED -> null;
        };
        if (next == null) {
            throw new InvalidScanException(this, scan.type());
        }
        return new Shipment(id, reference, origin, destination, next, createdAt);
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
