package fr.michael.logistics.shipment.application;

import fr.michael.logistics.shipment.domain.Anomaly;
import fr.michael.logistics.shipment.domain.AnomalyRules;
import fr.michael.logistics.shipment.domain.AnomalyType;
import fr.michael.logistics.shipment.domain.Shipment;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.transaction.support.TransactionOperations;

/**
 * Cas d'usage : ouvre les anomalies LATE et STALLED des expéditions non livrées ({@link AnomalyRules#detect}).
 * Appelé par une tâche planifiée, éventuellement sur plusieurs instances en même temps.
 */
public class DetectAnomaliesService {

    private final ShipmentRepository shipments;
    private final ScanRepository scans;
    private final AnomalyRepository anomalies;
    private final TransactionOperations transactions;
    private final Clock clock;
    private final Duration stalledAfter;

    public DetectAnomaliesService(
            ShipmentRepository shipments,
            ScanRepository scans,
            AnomalyRepository anomalies,
            TransactionOperations transactions,
            Clock clock,
            Duration stalledAfter) {
        this.shipments = shipments;
        this.scans = scans;
        this.anomalies = anomalies;
        this.transactions = transactions;
        this.clock = clock;
        this.stalledAfter = stalledAfter;
    }

    /**
     * Repère d'abord, sans verrou, les expéditions qui ont une anomalie pas encore ouverte ; puis, pour chacune,
     * dans sa propre transaction, verrouille l'expédition, relit son état et ouvre ce qui reste à ouvrir.
     *
     * @return nombre d'anomalies ouvertes par cet appel
     */
    public int detect() {
        Instant now = clock.instant();
        Set<OpenKey> open = anomalies.findOpen().stream()
                .map(a -> new OpenKey(a.shipmentId(), a.type()))
                .collect(Collectors.toSet());
        int opened = 0;
        for (ShipmentActivity activity : shipments.findUndeliveredActivity()) {
            UUID shipmentId = activity.shipment().id();
            boolean somethingToOpen = AnomalyRules.detect(activity.shipment(), activity.lastScanReceivedAt(), now, stalledAfter)
                    .stream()
                    .anyMatch(type -> !open.contains(new OpenKey(shipmentId, type)));
            if (somethingToOpen) {
                opened += transactions.execute(status -> openStillPresent(shipmentId, now));
            }
        }
        return opened;
    }

    /**
     * Le verrou sur l'expédition fait passer un scan concurrent avant ou après, jamais pendant : sinon une
     * STALLED pourrait s'ouvrir juste après que ce scan a fermé les anomalies, et rester ouverte à tort.
     */
    private int openStillPresent(UUID shipmentId, Instant now) {
        Shipment shipment = shipments.lockById(shipmentId).orElse(null);
        if (shipment == null) {
            return 0;
        }
        Instant lastScanReceivedAt = scans.lastReceivedAt(shipmentId).orElse(null);
        int opened = 0;
        for (AnomalyType type : AnomalyRules.detect(shipment, lastScanReceivedAt, now, stalledAfter)) {
            if (anomalies.openIfAbsent(Anomaly.open(shipmentId, type, now))) {
                opened++;
            }
        }
        return opened;
    }

    private record OpenKey(UUID shipmentId, AnomalyType type) {
    }
}
