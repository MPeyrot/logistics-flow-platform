package fr.michael.logistics.shipment.application;

import fr.michael.logistics.shipment.domain.Anomaly;
import fr.michael.logistics.shipment.domain.AnomalyRules;
import fr.michael.logistics.shipment.domain.AnomalyType;
import fr.michael.logistics.shipment.domain.Shipment;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionOperations;

/**
 * Cas d'usage : ouvre les anomalies LATE et STALLED des expéditions non livrées ({@link AnomalyRules#detect}).
 * Appelé par une tâche planifiée, éventuellement sur plusieurs instances en même temps.
 */
public class DetectAnomaliesService {

    private static final Logger log = LoggerFactory.getLogger(DetectAnomaliesService.class);

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
     * La base repère les expéditions qui ont une anomalie pas encore ouverte ; puis, pour chacune, dans sa propre
     * transaction, on verrouille l'expédition, relit son état et ouvre ce qui reste à ouvrir. Une expédition en
     * échec (verrou, deadlock, base coupée) est journalisée et n'arrête pas la passe : la suivante la reprendra.
     *
     * @return nombre d'anomalies ouvertes par cet appel
     */
    public int detect() {
        Instant now = clock.instant();
        int opened = 0;
        for (UUID shipmentId : shipments.findAnomalyCandidateIds(now, now.minus(stalledAfter))) {
            try {
                opened += transactions.execute(status -> openStillPresent(shipmentId, now));
            } catch (RuntimeException e) {
                log.warn("Détection d'anomalies impossible pour l'expédition {} : passe poursuivie", shipmentId, e);
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
}
