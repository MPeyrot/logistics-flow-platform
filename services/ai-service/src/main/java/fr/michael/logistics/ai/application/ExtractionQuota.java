package fr.michael.logistics.ai.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;

/** Plafond global d'extractions sur une heure glissante. Compteur en mémoire, propre à l'instance. */
public class ExtractionQuota {

    private static final Duration WINDOW = Duration.ofHours(1);

    private final int limit;
    private final Clock clock;
    private final Deque<Instant> accepted = new ArrayDeque<>();

    public ExtractionQuota(int limit, Clock clock) {
        if (limit < 1) {
            throw new IllegalArgumentException("The extraction limit must be at least 1");
        }
        this.limit = limit;
        this.clock = clock;
    }

    /** Réserve une extraction ou lève {@link ExtractionQuotaExceededException}. */
    public synchronized void acquire() {
        Instant now = clock.instant();
        while (!accepted.isEmpty() && !accepted.peekFirst().plus(WINDOW).isAfter(now)) {
            accepted.removeFirst();
        }
        if (accepted.size() >= limit) {
            Duration wait = Duration.between(now, accepted.peekFirst().plus(WINDOW));
            throw new ExtractionQuotaExceededException(Math.max(1, wait.toSeconds() + 1));
        }
        accepted.addLast(now);
    }

    /** Rend la dernière extraction réservée : aucun appel au modèle n'a été facturé. */
    public synchronized void release() {
        if (!accepted.isEmpty()) {
            accepted.removeLast();
        }
    }
}
