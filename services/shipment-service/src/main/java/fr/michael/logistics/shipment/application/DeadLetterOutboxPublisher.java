package fr.michael.logistics.shipment.application;

import java.time.Clock;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionOperations;

/**
 * Publie l'outbox des rejeux vers {@code scan-events}, dans l'ordre d'écriture (donc dans l'ordre de chaque partition
 * du DLT). Chaque message est marqué publié après l'accusé du broker ; un échec de publication arrête la passe, la
 * suivante repart de ce message. Au moins une fois : si le marquage n'est pas validé après l'accusé (base coupée),
 * le lot est republié ; l'écouteur est idempotent ({@code eventId}).
 */
public class DeadLetterOutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(DeadLetterOutboxPublisher.class);

    private final DeadLetterOutbox outbox;
    private final ScanEventDeadLetters deadLetters;
    private final TransactionOperations transactions;
    private final Clock clock;
    private final int batchSize;

    public DeadLetterOutboxPublisher(DeadLetterOutbox outbox, ScanEventDeadLetters deadLetters,
            TransactionOperations transactions, Clock clock, int batchSize) {
        this.outbox = outbox;
        this.deadLetters = deadLetters;
        this.transactions = transactions;
        this.clock = clock;
        this.batchSize = batchSize;
    }

    /** Publie les messages en attente, lot par lot, jusqu'à vider l'outbox ou un échec ; rend leur nombre. */
    public int publishPending() {
        int total = 0;
        while (true) {
            Batch batch = transactions.execute(status -> publishBatch());
            total += batch.published();
            if (batch.stopped()) {
                return total;
            }
        }
    }

    /** Un lot dans une transaction : le verrou et les marquages sont validés ensemble à la fin. */
    private Batch publishBatch() {
        if (!outbox.lockPublisher()) {
            return new Batch(0, true);
        }
        List<OutboxMessage> pending = outbox.pending(batchSize);
        int published = 0;
        for (OutboxMessage message : pending) {
            try {
                deadLetters.republish(message.key(), message.value());
            } catch (RuntimeException e) {
                log.warn("Rejeu du DLT non publié (outbox {}), nouvel essai à la prochaine passe : {}",
                        message.id(), e.getMessage());
                return new Batch(published, true);
            }
            outbox.markPublished(message.id(), clock.instant());
            published++;
        }
        return new Batch(published, pending.size() < batchSize);
    }

    /** {@code stopped} : outbox vidée, verrou tenu ailleurs ou échec de publication. */
    private record Batch(int published, boolean stopped) {
    }
}
