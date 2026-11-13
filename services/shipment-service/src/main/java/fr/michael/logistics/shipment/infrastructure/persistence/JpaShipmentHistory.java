package fr.michael.logistics.shipment.infrastructure.persistence;

import fr.michael.logistics.shipment.application.ScanRecord;
import fr.michael.logistics.shipment.application.ShipmentHistory;
import fr.michael.logistics.shipment.domain.Anomaly;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
class JpaShipmentHistory implements ShipmentHistory {

    private final SpringDataScanRepository scans;
    private final SpringDataAnomalyRepository anomalies;

    JpaShipmentHistory(SpringDataScanRepository scans, SpringDataAnomalyRepository anomalies) {
        this.scans = scans;
        this.anomalies = anomalies;
    }

    @Override
    public List<ScanRecord> scans(UUID shipmentId) {
        return scans.findByShipmentIdOrderByOccurredAtAscReceivedAtAsc(shipmentId).stream()
                .map(ScanEventJpaEntity::toRecord)
                .toList();
    }

    @Override
    public List<Anomaly> anomalies(UUID shipmentId) {
        return anomalies.findByShipmentIdOrderByDetectedAtAsc(shipmentId).stream()
                .map(AnomalyJpaEntity::toDomain)
                .toList();
    }
}
