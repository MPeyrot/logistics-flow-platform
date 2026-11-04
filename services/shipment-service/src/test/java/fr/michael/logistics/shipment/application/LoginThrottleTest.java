package fr.michael.logistics.shipment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class LoginThrottleTest {

    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-03T10:00:00Z"));
    private final LoginThrottle throttle = new LoginThrottle(5, Duration.ofMinutes(15), Duration.ofMinutes(15), clock);

    @Test
    void fifthFailureBlocksTheUsernameForFifteenMinutes() {
        fail("paul", 4);
        assertThatCode(() -> throttle.begin("paul")).doesNotThrowAnyException();
        throttle.failed("paul");

        assertThatThrownBy(() -> throttle.begin("paul"))
                .isInstanceOfSatisfying(LoginBlockedException.class,
                        e -> assertThat(e.retryAfterSeconds()).isEqualTo(900));

        clock.advance(Duration.ofMinutes(10));
        assertThatThrownBy(() -> throttle.begin("paul"))
                .isInstanceOfSatisfying(LoginBlockedException.class,
                        e -> assertThat(e.retryAfterSeconds()).isEqualTo(300));

        clock.advance(Duration.ofMinutes(5));
        assertThatCode(() -> throttle.begin("paul")).doesNotThrowAnyException();
    }

    @Test
    void failuresOlderThanTheWindowAreForgotten() {
        fail("paul", 4);
        clock.advance(Duration.ofMinutes(15));

        fail("paul", 4);
        assertThatCode(() -> throttle.begin("paul")).doesNotThrowAnyException();
    }

    @Test
    void successResetsTheCounter() {
        fail("paul", 4);
        throttle.begin("paul");
        throttle.succeeded("paul");

        fail("paul", 4);
        assertThatCode(() -> throttle.begin("paul")).doesNotThrowAnyException();
    }

    @Test
    void usernameIsCountedWhateverTheCaseAndIndependentlyOfOthers() {
        fail("Paul", 2);
        fail("PAUL", 3);

        assertThatThrownBy(() -> throttle.begin("paul")).isInstanceOf(LoginBlockedException.class);
        assertThatCode(() -> throttle.begin("pierre")).doesNotThrowAnyException();
    }

    /** Tentatives simultanées : chacune compte dès son début, sans attendre la vérification du mot de passe. */
    @Test
    void attemptsInFlightCountTowardsTheLimit() {
        for (int i = 0; i < 5; i++) {
            throttle.begin("paul");
        }

        assertThatThrownBy(() -> throttle.begin("paul")).isInstanceOf(LoginBlockedException.class);
    }

    @Test
    void thresholdsAreConfigurable() {
        LoginThrottle strict = new LoginThrottle(2, Duration.ofMinutes(1), Duration.ofSeconds(30), clock);
        for (int i = 0; i < 2; i++) {
            strict.begin("paul");
            strict.failed("paul");
        }

        assertThatThrownBy(() -> strict.begin("paul"))
                .isInstanceOfSatisfying(LoginBlockedException.class,
                        e -> assertThat(e.retryAfterSeconds()).isEqualTo(30));
    }

    /** Identifiants tous différents (inconnus compris), tous dans la fenêtre : la table reste bornée. */
    @Test
    void tracksAtMostTenThousandUsernames() {
        for (int i = 0; i < 25_000; i++) {
            throttle.begin("user-" + i);
            throttle.failed("user-" + i);
        }

        assertThat(throttle.trackedUsernames()).isEqualTo(LoginThrottle.DEFAULT_MAX_TRACKED_USERNAMES);
    }

    @Test
    void theLeastRecentlySeenUsernamesAreForgottenFirst() {
        fail("old", 4);
        fail("paul", 4);
        for (int i = 0; i < LoginThrottle.DEFAULT_MAX_TRACKED_USERNAMES - 2; i++) {
            throttle.begin("user-" + i);
        }

        throttle.begin("one-more");

        assertThat(throttle.trackedUsernames()).isEqualTo(LoginThrottle.DEFAULT_MAX_TRACKED_USERNAMES);
        fail("paul", 1);
        assertThatThrownBy(() -> throttle.begin("paul")).isInstanceOf(LoginBlockedException.class);
        fail("old", 4);
        assertThatCode(() -> throttle.begin("old")).doesNotThrowAnyException();
    }

    /** 10 000 tentatives sous d'autres identifiants ne font pas oublier un blocage (#28). */
    @Test
    void saturatingWithUnblockedUsernamesKeepsTheBlock() {
        fail("paul", 5);

        for (int i = 0; i < 25_000; i++) {
            throttle.begin("user-" + i);
            throttle.failed("user-" + i);
        }

        assertThatThrownBy(() -> throttle.begin("paul"))
                .isInstanceOfSatisfying(LoginBlockedException.class,
                        e -> assertThat(e.retryAfterSeconds()).isEqualTo(900));
        assertThat(throttle.trackedUsernames()).isEqualTo(LoginThrottle.DEFAULT_MAX_TRACKED_USERNAMES + 1);
    }

    @Test
    void aBlockedUsernameIsForgottenAtTheEndOfItsBlock() {
        LoginThrottle small = new LoginThrottle(1, Duration.ofMinutes(15), Duration.ofMinutes(15), 2, 2, clock);
        small.begin("paul");
        small.failed("paul");
        assertThat(small.trackedUsernames()).isEqualTo(1);

        clock.advance(Duration.ofMinutes(15));
        small.begin("pierre");

        assertThat(small.trackedUsernames()).isEqualTo(1);
        assertThatCode(() -> small.begin("paul")).doesNotThrowAnyException();
    }

    /** Second plafond : au-delà, le blocage le plus proche de sa fin est oublié ; la mémoire reste bornée. */
    @Test
    void beyondTheBlockedCapTheBlockClosestToItsEndIsForgotten() {
        LoginThrottle small = new LoginThrottle(1, Duration.ofMinutes(15), Duration.ofMinutes(15), 2, 2, clock);
        for (String username : new String[] {"anne", "bruno", "chloe"}) {
            small.begin(username);
            small.failed(username);
            clock.advance(Duration.ofMinutes(1));
        }
        for (int i = 0; i < 10; i++) {
            small.begin("user-" + i);
        }

        assertThat(small.trackedUsernames()).isEqualTo(4);
        assertThatCode(() -> small.begin("anne")).doesNotThrowAnyException();
        assertThatThrownBy(() -> small.begin("bruno")).isInstanceOf(LoginBlockedException.class);
        assertThatThrownBy(() -> small.begin("chloe")).isInstanceOf(LoginBlockedException.class);
    }

    @Test
    void capsMustBePositive() {
        assertThatThrownBy(() -> new LoginThrottle(5, Duration.ofMinutes(15), Duration.ofMinutes(15), 0, 10, clock))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LoginThrottle(5, Duration.ofMinutes(15), Duration.ofMinutes(15), 10, 0, clock))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private void fail(String username, int times) {
        for (int i = 0; i < times; i++) {
            throttle.begin(username);
            throttle.failed(username);
        }
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }
}
