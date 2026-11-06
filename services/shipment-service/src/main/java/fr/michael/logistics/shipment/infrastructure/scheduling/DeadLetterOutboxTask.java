package fr.michael.logistics.shipment.infrastructure.scheduling;

import fr.michael.logistics.shipment.application.DeadLetterOutboxPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Publie l'outbox des rejeux du DLT toutes les {@code shipment.kafka.dlt.outbox.interval}. Chaque instance lance la
 * sienne ; le verrou du publieur en laisse passer une seule à la fois.
 */
@Component
class DeadLetterOutboxTask {

    private static final Logger log = LoggerFactory.getLogger(DeadLetterOutboxTask.class);

    private final DeadLetterOutboxPublisher publisher;

    DeadLetterOutboxTask(DeadLetterOutboxPublisher publisher) {
        this.publisher = publisher;
    }

    @Scheduled(fixedDelayString = "${shipment.kafka.dlt.outbox.interval:1s}")
    void publish() {
        try {
            int published = publisher.publishPending();
            if (published > 0) {
                log.info("{} rejeu(x) du DLT publié(s)", published);
            }
        } catch (RuntimeException e) {
            // Base indisponible : nouvel essai à la passe suivante, sans pile d'appels à chaque seconde.
            log.warn("Outbox des rejeux du DLT non lue : {}", e.getMessage());
        }
    }
}
