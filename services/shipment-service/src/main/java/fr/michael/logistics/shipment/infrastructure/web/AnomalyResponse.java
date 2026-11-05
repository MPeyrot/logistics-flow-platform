package fr.michael.logistics.shipment.infrastructure.web;

import fr.michael.logistics.shipment.application.AnomalyView;
import java.time.Instant;
import java.util.UUID;

record AnomalyResponse(UUID id, String shipmentReference, String type, Instant detectedAt, Instant resolvedAt) {

    static AnomalyResponse from(AnomalyView view) {
        return new AnomalyResponse(view.id(), view.shipmentReference(), view.type().name(), view.detectedAt(),
                view.resolvedAt());
    }
}
