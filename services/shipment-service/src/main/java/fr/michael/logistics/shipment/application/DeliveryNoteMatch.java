package fr.michael.logistics.shipment.application;

import fr.michael.logistics.shipment.domain.Shipment;
import java.util.Optional;

/** Bon de livraison lu, et l'expédition qui porte la même référence si elle existe. */
public record DeliveryNoteMatch(DeliveryNoteReading reading, Optional<Shipment> shipment) {
}
