package fr.michael.logistics.shipment.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/**
 * Limite le nombre d'extractions par utilisateur sur une fenêtre glissante (20 par heure par défaut). Compteur en
 * mémoire, propre à l'instance. Une demande refusée ne compte pas.
 */
public class ExtractionRateLimiter {

    /** Au-delà, les utilisateurs sans extraction récente sont oubliés, pour borner la mémoire. */
    private static final int SWEEP_THRESHOLD = 10_000;

    private final int maxPerWindow;
    private final Duration window;
    private final Clock clock;
    private final Map<String, Deque<Instant>> byUser = new HashMap<>();

    public ExtractionRateLimiter(int maxPerWindow, Duration window, Clock clock) {
        if (maxPerWindow < 1 || window.isNegative() || window.isZero()) {
            throw new IllegalArgumentException("Extraction limit needs at least 1 request and a positive window");
        }
        this.maxPerWindow = maxPerWindow;
        this.window = window;
        this.clock = clock;
    }

    /** Enregistre une extraction pour cet utilisateur, ou lève {@link ExtractionRateLimitedException}. */
    public synchronized void acquire(String user) {
        Instant now = clock.instant();
        Instant limit = now.minus(window);
        if (byUser.size() > SWEEP_THRESHOLD) {
            byUser.values().removeIf(times -> times.isEmpty() || !times.peekLast().isAfter(limit));
        }
        Deque<Instant> times = byUser.computeIfAbsent(user, k -> new ArrayDeque<>());
        while (!times.isEmpty() && !times.peekFirst().isAfter(limit)) {
            times.removeFirst();
        }
        if (times.size() >= maxPerWindow) {
            long millis = Duration.between(now, times.peekFirst().plus(window)).toMillis();
            throw new ExtractionRateLimitedException(
                    "Extraction limit reached (" + maxPerWindow + " per " + window + "), retry later",
                    Math.max(1, (millis + 999) / 1000));
        }
        times.addLast(now);
    }

    /** Rend la dernière extraction prise par cet utilisateur : elle n'a rien coûté. */
    public synchronized void release(String user) {
        Deque<Instant> times = byUser.get(user);
        if (times != null && !times.isEmpty()) {
            times.removeLast();
        }
    }
}
