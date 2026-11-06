package fr.michael.logistics.shipment.application;

import fr.michael.logistics.shipment.domain.AnomalyType;
import java.time.Instant;
import java.util.UUID;

/** Anomalie vue de l'extérieur : rattachée à la référence de son expédition. */
public record AnomalyView(UUID id, String shipmentReference, AnomalyType type, Instant detectedAt, Instant resolvedAt) {
}
