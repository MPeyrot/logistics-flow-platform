package fr.michael.logistics.shipment.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
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
 *
 * <p>Mémoire bornée par deux tables. Les identifiants non bloqués (au plus {@code maxTrackedUsernames}) : au-delà, le
 * moins récemment vu est oublié. Les identifiants bloqués, à part : un blocage n'est oublié qu'à sa fin, sauf au-delà
 * de {@code maxBlockedUsernames}, où celui qui finit le plus tôt est oublié. Ainsi, des tentatives sous d'autres
 * identifiants ne lèvent pas un blocage.
 */
public class LoginThrottle {

    public static final int DEFAULT_MAX_TRACKED_USERNAMES = 10_000;
    public static final int DEFAULT_MAX_BLOCKED_USERNAMES = 100_000;

    private final int maxFailures;
    private final Duration failureWindow;
    private final Duration blockDuration;
    private final int maxBlockedUsernames;
    private final Clock clock;
    /** Ordre d'accès : l'aîné est l'identifiant vu le moins récemment, retiré en O(1) quand la table déborde. */
    private final Map<String, Deque<Instant>> recentFailures;
    /**
     * Fin de blocage par identifiant, dans l'ordre d'insertion. La durée de blocage étant fixe, c'est aussi l'ordre
     * des fins : les blocages terminés se retirent par la tête, sans parcourir la table.
     */
    private final Map<String, Instant> blockedUntil = new LinkedHashMap<>();

    public LoginThrottle(int maxFailures, Duration failureWindow, Duration blockDuration, Clock clock) {
        this(maxFailures, failureWindow, blockDuration, DEFAULT_MAX_TRACKED_USERNAMES, DEFAULT_MAX_BLOCKED_USERNAMES,
                clock);
    }

    public LoginThrottle(int maxFailures, Duration failureWindow, Duration blockDuration, int maxTrackedUsernames,
            int maxBlockedUsernames, Clock clock) {
        if (maxFailures < 1 || failureWindow.isNegative() || failureWindow.isZero()
                || blockDuration.isNegative() || blockDuration.isZero()) {
            throw new IllegalArgumentException("Login throttle needs at least 1 failure and positive durations");
        }
        if (maxTrackedUsernames < 1 || maxBlockedUsernames < 1) {
            throw new IllegalArgumentException("Login throttle needs room for at least 1 username in each table");
        }
        this.maxFailures = maxFailures;
        this.failureWindow = failureWindow;
        this.blockDuration = blockDuration;
        this.maxBlockedUsernames = maxBlockedUsernames;
        this.clock = clock;
        this.recentFailures = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Deque<Instant>> eldest) {
                return size() > maxTrackedUsernames;
            }
        };
    }

    /** À appeler avant de vérifier le mot de passe. Lève {@link LoginBlockedException} si l'identifiant est bloqué. */
    public synchronized void begin(String username) {
        Instant now = clock.instant();
        forgetEndedBlocks(now);
        String key = key(username);
        Instant until = blockedUntil.get(key);
        if (until != null) {
            throw new LoginBlockedException(secondsBetween(now, until));
        }
        Deque<Instant> recent = recentFailures.computeIfAbsent(key, k -> new ArrayDeque<>());
        Instant limit = now.minus(failureWindow);
        while (!recent.isEmpty() && !recent.peekFirst().isAfter(limit)) {
            recent.removeFirst();
        }
        if (recent.size() >= maxFailures) {
            // Les tentatives en cours atteignent déjà le seuil : elles bloqueront l'identifiant si elles échouent.
            throw new LoginBlockedException(blockDuration.toSeconds());
        }
        recent.addLast(now);
    }

    public synchronized void failed(String username) {
        String key = key(username);
        Deque<Instant> recent = recentFailures.get(key);
        if (recent != null && recent.size() >= maxFailures) {
            Instant now = clock.instant();
            forgetEndedBlocks(now);
            recentFailures.remove(key);
            blockedUntil.put(key, now.plus(blockDuration));
            if (blockedUntil.size() > maxBlockedUsernames) {
                Iterator<String> closestToItsEnd = blockedUntil.keySet().iterator();
                closestToItsEnd.next();
                closestToItsEnd.remove();
            }
        }
    }

    /** La tentative n'a rien vérifié (erreur inattendue, base indisponible) : elle ne compte pas comme un échec. */
    public synchronized void cancel(String username) {
        Deque<Instant> recent = recentFailures.get(key(username));
        if (recent != null && !recent.isEmpty()) {
            recent.removeLast();
        }
    }

    public synchronized void succeeded(String username) {
        String key = key(username);
        recentFailures.remove(key);
        blockedUntil.remove(key);
    }

    synchronized int trackedUsernames() {
        return recentFailures.size() + blockedUntil.size();
    }

    private void forgetEndedBlocks(Instant now) {
        Iterator<Instant> ends = blockedUntil.values().iterator();
        while (ends.hasNext()) {
            if (now.isBefore(ends.next())) {
                return;
            }
            ends.remove();
        }
    }

    private static String key(String username) {
        return username == null ? "" : username.toLowerCase(Locale.ROOT);
    }

    private static long secondsBetween(Instant now, Instant until) {
        long millis = Duration.between(now, until).toMillis();
        return Math.max(1, (millis + 999) / 1000);
    }
}
