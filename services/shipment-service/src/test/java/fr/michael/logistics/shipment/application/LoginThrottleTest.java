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
