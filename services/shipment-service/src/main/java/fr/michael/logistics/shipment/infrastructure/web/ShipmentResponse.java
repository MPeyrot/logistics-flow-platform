package fr.michael.logistics.shipment.infrastructure.web;

import fr.michael.logistics.shipment.domain.Shipment;
import java.time.Instant;
import java.util.UUID;

record ShipmentResponse(
        UUID id,
        String reference,
        String origin,
        String destination,
        String status,
        Instant createdAt,
        Instant expectedDeliveryAt) {

    static ShipmentResponse from(Shipment shipment) {
        return new ShipmentResponse(
                shipment.id(),
                shipment.reference(),
                shipment.origin(),
                shipment.destination(),
                shipment.status().name(),
                shipment.createdAt(),
                shipment.expectedDeliveryAt());
    }
}
