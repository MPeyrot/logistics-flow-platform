package fr.michael.logistics.shipment.application;

import fr.michael.logistics.shipment.domain.Anomaly;
import fr.michael.logistics.shipment.domain.Shipment;
import java.util.List;

/** Une expédition et ses anomalies ouvertes. */
public record ShipmentWithAnomalies(Shipment shipment, List<Anomaly> openAnomalies) {
}
