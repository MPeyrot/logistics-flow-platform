package fr.michael.logistics.shipment.application;

import fr.michael.logistics.shipment.domain.Shipment;
import java.time.Instant;

/** Une expédition et l'heure de réception de son dernier scan appliqué (null si jamais scannée). */
public record ShipmentActivity(Shipment shipment, Instant lastScanReceivedAt) {
}
