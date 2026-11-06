package fr.michael.logistics.shipment.application;

import java.util.Optional;

/** Bon de livraison lu, et l'expédition qui porte la même référence si elle existe, avec ses anomalies ouvertes. */
public record DeliveryNoteMatch(DeliveryNoteReading reading, Optional<ShipmentWithAnomalies> shipment) {
}
