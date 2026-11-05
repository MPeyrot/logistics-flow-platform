package fr.michael.logistics.shipment.application;

import fr.michael.logistics.shipment.domain.Shipment;
import java.time.Instant;

/** Une expédition et l'instant de son dernier scan appliqué ({@code lastScanAt} null si jamais scannée). */
public record ShipmentActivity(Shipment shipment, Instant lastScanAt) {
}
