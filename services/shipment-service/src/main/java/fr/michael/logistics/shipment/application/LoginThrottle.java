package fr.michael.logistics.shipment.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
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

    /** Au-delà, les identifiants sans échec récent sont oubliés, pour borner la mémoire. */
    private static final int SWEEP_THRESHOLD = 10_000;

    private final int maxFailures;
    private final Duration failureWindow;
    private final Duration blockDuration;
    private final Clock clock;
    private final Map<String, Attempts> byUsername = new HashMap<>();

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
        if (byUsername.size() > SWEEP_THRESHOLD) {
            byUsername.values().removeIf(attempts -> attempts.forgettable(now));
        }
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

    private static String key(String username) {
        return username == null ? "" : username.toLowerCase(Locale.ROOT);
    }

    private static long secondsBetween(Instant now, Instant until) {
        long millis = Duration.between(now, until).toMillis();
        return Math.max(1, (millis + 999) / 1000);
    }

    private final class Attempts {
        private final Deque<Instant> recent = new ArrayDeque<>();
        private Instant blockedUntil;

        void forgetOlderThan(Instant limit) {
            while (!recent.isEmpty() && !recent.peekFirst().isAfter(limit)) {
                recent.removeFirst();
            }
        }

        boolean forgettable(Instant now) {
            boolean blocked = blockedUntil != null && now.isBefore(blockedUntil);
            return !blocked && (recent.isEmpty() || !recent.peekLast().isAfter(now.minus(failureWindow)));
        }
    }
}
