package fr.michael.logistics.shipment.application;

import fr.michael.logistics.shipment.domain.Anomaly;
import fr.michael.logistics.shipment.domain.Shipment;
import java.util.List;

/** Une expédition avec l'historique de ses scans (chronologique) et toutes ses anomalies, ouvertes ou closes. */
public record ShipmentDetail(Shipment shipment, List<ScanRecord> scans, List<Anomaly> anomalies) {

    public List<Anomaly> openAnomalies() {
        return anomalies.stream().filter(Anomaly::isOpen).toList();
    }
}
