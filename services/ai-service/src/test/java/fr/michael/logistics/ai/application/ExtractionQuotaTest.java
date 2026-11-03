package fr.michael.logistics.ai.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class ExtractionQuotaTest {

    private final MutableClock clock = new MutableClock();

    @Test
    void refusesBeyondTheLimitWithTimeBeforeNextSlot() {
        var quota = new ExtractionQuota(2, clock);
        quota.acquire();
        clock.advanceSeconds(600);
        quota.acquire();

        assertThatThrownBy(quota::acquire)
                .isInstanceOfSatisfying(ExtractionQuotaExceededException.class,
                        e -> assertThat(e.retryAfterSeconds()).isBetween(3000L, 3010L));
    }

    @Test
    void freesASlotOnceTheOldestExtractionLeavesTheHour() {
        var quota = new ExtractionQuota(1, clock);
        quota.acquire();
        clock.advanceSeconds(3599);
        assertThatThrownBy(quota::acquire).isInstanceOf(ExtractionQuotaExceededException.class);

        clock.advanceSeconds(1);
        assertThatCode(quota::acquire).doesNotThrowAnyException();
    }

    @Test
    void rejectsLimitBelowOne() {
        assertThatThrownBy(() -> new ExtractionQuota(0, clock)).isInstanceOf(IllegalArgumentException.class);
    }

    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-11-03T10:00:00Z");

        void advanceSeconds(long seconds) {
            now = now.plusSeconds(seconds);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
