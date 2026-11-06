package fr.michael.logistics.shipment.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;

/** Règles de détection et de clôture des anomalies, sans état ni horloge : l'instant courant est passé. */
public final class AnomalyRules {

    private AnomalyRules() {
    }

    /**
     * Anomalies présentes à l'instant {@code now}. Une expédition livrée n'en a aucune.
     * LATE : {@code expectedDeliveryAt} dépassé (jamais si elle est null, expéditions antérieures à ce champ).
     * STALLED : dernier scan reçu, ou création sans scan, vieux d'au moins {@code stalledAfter}.
     *
     * @param lastScanReceivedAt heure à laquelle le service a reçu le dernier scan appliqué, pas sa date métier
     *        {@code occurredAt} fournie par le client (un scan en retard ou daté dans le futur fausserait la
     *        règle) ; null si l'expédition n'a jamais été scannée
     */
    public static Set<AnomalyType> detect(
            Shipment shipment, Instant lastScanReceivedAt, Instant now, Duration stalledAfter) {
        Set<AnomalyType> found = EnumSet.noneOf(AnomalyType.class);
        if (shipment.status() == ShipmentStatus.DELIVERED) {
            return found;
        }
        if (shipment.expectedDeliveryAt() != null && now.isAfter(shipment.expectedDeliveryAt())) {
            found.add(AnomalyType.LATE);
        }
        Instant lastActivity = lastScanReceivedAt != null ? lastScanReceivedAt : shipment.createdAt();
        if (!lastActivity.plus(stalledAfter).isAfter(now)) {
            found.add(AnomalyType.STALLED);
        }
        return found;
    }

    /** Anomalies fermées par un scan appliqué : STALLED toujours, LATE quand l'expédition est livrée. */
    public static Set<AnomalyType> resolvedByScan(Shipment afterScan) {
        return afterScan.status() == ShipmentStatus.DELIVERED
                ? EnumSet.allOf(AnomalyType.class)
                : EnumSet.of(AnomalyType.STALLED);
    }
}
