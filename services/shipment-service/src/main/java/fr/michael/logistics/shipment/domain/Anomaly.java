package fr.michael.logistics.shipment.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Anomalie d'une expédition. Ouverte tant que {@code resolvedAt} est null ; au plus une ouverte par type. */
public record Anomaly(UUID id, UUID shipmentId, AnomalyType type, Instant detectedAt, Instant resolvedAt) {

    public Anomaly {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(shipmentId, "shipmentId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(detectedAt, "detectedAt");
    }

    public static Anomaly open(UUID shipmentId, AnomalyType type, Instant detectedAt) {
        return new Anomaly(UUID.randomUUID(), shipmentId, type, detectedAt, null);
    }

    public boolean isOpen() {
        return resolvedAt == null;
    }
}
