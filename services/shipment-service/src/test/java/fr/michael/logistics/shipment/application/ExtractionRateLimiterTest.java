package fr.michael.logistics.shipment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class ExtractionRateLimiterTest {

    private final MutableClock clock = new MutableClock();
    private final ExtractionRateLimiter limiter = new ExtractionRateLimiter(2, Duration.ofHours(1), clock);

    @Test
    void refusesBeyondTheLimitWithTimeUntilTheOldestExpires() {
        limiter.acquire("alice");
        clock.advance(Duration.ofMinutes(10));
        limiter.acquire("alice");

        assertThatThrownBy(() -> limiter.acquire("alice"))
                .isInstanceOfSatisfying(ExtractionRateLimitedException.class,
                        e -> assertThat(e.retryAfterSeconds()).isEqualTo(50 * 60));
    }

    @Test
    void countsPerUser() {
        limiter.acquire("alice");
        limiter.acquire("alice");

        assertThatCode(() -> limiter.acquire("bob")).doesNotThrowAnyException();
    }

    @Test
    void allowsAgainOnceTheWindowHasPassed() {
        limiter.acquire("alice");
        limiter.acquire("alice");
        clock.advance(Duration.ofHours(1));

        assertThatCode(() -> limiter.acquire("alice")).doesNotThrowAnyException();
    }

    @Test
    void aRefusedRequestDoesNotCount() {
        limiter.acquire("alice");
        limiter.acquire("alice");
        assertThatThrownBy(() -> limiter.acquire("alice")).isInstanceOf(ExtractionRateLimitedException.class);
        clock.advance(Duration.ofHours(1));

        limiter.acquire("alice");
        limiter.acquire("alice");
        assertThatThrownBy(() -> limiter.acquire("alice")).isInstanceOf(ExtractionRateLimitedException.class);
    }

    static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-11-03T10:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }
    }
}
