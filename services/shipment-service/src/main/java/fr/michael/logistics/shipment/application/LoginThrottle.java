package fr.michael.logistics.shipment.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Freine la force brute sur la connexion : après {@code maxFailures} échecs en {@code failureWindow} pour un même
 * identifiant (sans distinction de casse, qu'il existe ou non), il est bloqué {@code blockDuration}, même avec le bon
 * mot de passe. Compteur en mémoire, propre à l'instance.
 *
 * <p>Chaque tentative compte comme un échec dès {@link #begin} : des requêtes simultanées ne passent pas toutes avant
 * que le premier échec soit noté. {@link #succeeded} remet le compteur à zéro.
 */
public class LoginThrottle {

    /** Au-delà, l'identifiant vu le moins récemment est oublié, pour borner la mémoire (même s'il est bloqué). */
    static final int MAX_TRACKED_USERNAMES = 10_000;

    private final int maxFailures;
    private final Duration failureWindow;
    private final Duration blockDuration;
    private final Clock clock;
    /** Ordre d'accès : l'aîné est l'identifiant vu le moins récemment, retiré en O(1) quand la table déborde. */
    private final Map<String, Attempts> byUsername = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Attempts> eldest) {
            return size() > MAX_TRACKED_USERNAMES;
        }
    };

    public LoginThrottle(int maxFailures, Duration failureWindow, Duration blockDuration, Clock clock) {
        if (maxFailures < 1 || failureWindow.isNegative() || failureWindow.isZero()
                || blockDuration.isNegative() || blockDuration.isZero()) {
            throw new IllegalArgumentException("Login throttle needs at least 1 failure and positive durations");
        }
        this.maxFailures = maxFailures;
        this.failureWindow = failureWindow;
        this.blockDuration = blockDuration;
        this.clock = clock;
    }

    /** À appeler avant de vérifier le mot de passe. Lève {@link LoginBlockedException} si l'identifiant est bloqué. */
    public synchronized void begin(String username) {
        Instant now = clock.instant();
        Attempts attempts = byUsername.computeIfAbsent(key(username), k -> new Attempts());
        if (attempts.blockedUntil != null) {
            if (now.isBefore(attempts.blockedUntil)) {
                throw new LoginBlockedException(secondsBetween(now, attempts.blockedUntil));
            }
            attempts.blockedUntil = null;
        }
        attempts.forgetOlderThan(now.minus(failureWindow));
        if (attempts.recent.size() >= maxFailures) {
            // Les tentatives en cours atteignent déjà le seuil : elles bloqueront l'identifiant si elles échouent.
            throw new LoginBlockedException(blockDuration.toSeconds());
        }
        attempts.recent.addLast(now);
    }

    public synchronized void failed(String username) {
        Attempts attempts = byUsername.get(key(username));
        if (attempts != null && attempts.blockedUntil == null && attempts.recent.size() >= maxFailures) {
            attempts.blockedUntil = clock.instant().plus(blockDuration);
            attempts.recent.clear();
        }
    }

    public synchronized void succeeded(String username) {
        byUsername.remove(key(username));
    }

    synchronized int trackedUsernames() {
        return byUsername.size();
    }

    private static String key(String username) {
        return username == null ? "" : username.toLowerCase(Locale.ROOT);
    }

    private static long secondsBetween(Instant now, Instant until) {
        long millis = Duration.between(now, until).toMillis();
        return Math.max(1, (millis + 999) / 1000);
    }

    private static final class Attempts {
        private final Deque<Instant> recent = new ArrayDeque<>();
        private Instant blockedUntil;

        void forgetOlderThan(Instant limit) {
            while (!recent.isEmpty() && !recent.peekFirst().isAfter(limit)) {
                recent.removeFirst();
            }
        }
    }
}
