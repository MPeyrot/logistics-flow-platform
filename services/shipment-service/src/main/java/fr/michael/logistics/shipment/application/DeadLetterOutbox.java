package fr.michael.logistics.shipment.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Port de sortie : outbox des rejeux du DLT, écrite avec {@code dlt_replay} dans la même transaction
 * ({@link DeadLetterReplays#record}). Chaque méthode s'exécute dans la transaction de l'appelant.
 */
public interface DeadLetterOutbox {

    /** Met le message dans l'outbox. {@code jobId} : le « tout rejouer » qui le rejoue, {@code null} pour un seul. */
    void add(DeadLetter letter, Instant createdAt, UUID jobId);

    /**
     * Verrou du publieur, tenu jusqu'à la fin de la transaction : une seule publication à la fois, toutes instances
     * comprises, pour garder l'ordre d'écriture. {@code false} si une autre transaction le tient déjà.
     */
    boolean lockPublisher();

    /** Au plus {@code limit} messages pas encore publiés, dans l'ordre d'écriture. */
    List<OutboxMessage> pending(int limit);

    void markPublished(long id, Instant publishedAt);
}
