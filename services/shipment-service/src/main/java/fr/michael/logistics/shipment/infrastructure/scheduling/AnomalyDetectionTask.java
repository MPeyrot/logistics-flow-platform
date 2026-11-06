package fr.michael.logistics.shipment.infrastructure.scheduling;

import fr.michael.logistics.shipment.application.DetectAnomaliesService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Lance la détection toutes les {@code shipment.anomaly.detection-interval}, la première fois un intervalle après
 * le démarrage. Chaque instance lance la sienne : l'index unique partiel évite les doublons.
 */
@Component
class AnomalyDetectionTask {

    private static final Logger log = LoggerFactory.getLogger(AnomalyDetectionTask.class);

    private final DetectAnomaliesService detection;

    AnomalyDetectionTask(DetectAnomaliesService detection) {
        this.detection = detection;
    }

    @Scheduled(
            fixedDelayString = "${shipment.anomaly.detection-interval}",
            initialDelayString = "${shipment.anomaly.detection-interval}")
    void detect() {
        int opened = detection.detect();
        if (opened > 0) {
            log.info("{} anomalie(s) ouverte(s)", opened);
        }
    }
}
