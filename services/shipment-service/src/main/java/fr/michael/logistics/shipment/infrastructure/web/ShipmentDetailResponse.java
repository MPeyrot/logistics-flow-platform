package fr.michael.logistics.shipment.infrastructure.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import fr.michael.logistics.shipment.application.ScanRecord;
import fr.michael.logistics.shipment.application.ShipmentDetail;
import fr.michael.logistics.shipment.domain.Anomaly;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** {@link ShipmentResponse} plus l'historique des scans et toutes les anomalies. */
record ShipmentDetailResponse(
        UUID id,
        String reference,
        String origin,
        String destination,
        String status,
        Instant createdAt,
        Instant expectedDeliveryAt,
        List<ShipmentResponse.OpenAnomaly> openAnomalies,
        List<Scan> scans,
        List<AnomalyEntry> anomalies) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Scan(UUID eventId, String type, String site, Instant occurredAt, Instant receivedAt, String reason) {

        static Scan from(ScanRecord scan) {
            return new Scan(scan.eventId(), scan.type().name(), scan.site(), scan.occurredAt(), scan.receivedAt(),
                    scan.reason());
        }
    }

    record AnomalyEntry(UUID id, String type, Instant detectedAt, Instant resolvedAt) {

        static AnomalyEntry from(Anomaly anomaly) {
            return new AnomalyEntry(anomaly.id(), anomaly.type().name(), anomaly.detectedAt(), anomaly.resolvedAt());
        }
    }

    static ShipmentDetailResponse from(ShipmentDetail detail) {
        ShipmentResponse base = ShipmentResponse.from(detail.shipment(), detail.openAnomalies());
        return new ShipmentDetailResponse(base.id(), base.reference(), base.origin(), base.destination(),
                base.status(), base.createdAt(), base.expectedDeliveryAt(), base.openAnomalies(),
                detail.scans().stream().map(Scan::from).toList(),
                detail.anomalies().stream().map(AnomalyEntry::from).toList());
    }
}
