package fr.michael.logistics.shipment.infrastructure.web;

import fr.michael.logistics.shipment.application.ShipmentWithAnomalies;
import fr.michael.logistics.shipment.domain.Anomaly;
import fr.michael.logistics.shipment.domain.Shipment;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

record ShipmentResponse(
        UUID id,
        String reference,
        String origin,
        String destination,
        String status,
        Instant createdAt,
        Instant expectedDeliveryAt,
        List<OpenAnomaly> openAnomalies) {

    record OpenAnomaly(String type, Instant detectedAt) {
    }

    static ShipmentResponse from(Shipment shipment) {
        return from(shipment, List.of());
    }

    static ShipmentResponse from(ShipmentWithAnomalies entry) {
        return from(entry.shipment(), entry.openAnomalies());
    }

    static ShipmentResponse from(Shipment shipment, List<Anomaly> openAnomalies) {
        return new ShipmentResponse(
                shipment.id(),
                shipment.reference(),
                shipment.origin(),
                shipment.destination(),
                shipment.status().name(),
                shipment.createdAt(),
                shipment.expectedDeliveryAt(),
                openAnomalies.stream()
                        .map(anomaly -> new OpenAnomaly(anomaly.type().name(), anomaly.detectedAt()))
                        .toList());
    }
}
