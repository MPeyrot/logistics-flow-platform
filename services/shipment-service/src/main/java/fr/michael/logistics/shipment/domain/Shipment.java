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

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
