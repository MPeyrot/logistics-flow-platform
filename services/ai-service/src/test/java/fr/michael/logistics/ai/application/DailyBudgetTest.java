package fr.michael.logistics.ai.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class DailyBudgetTest {

    private final MutableClock clock = new MutableClock();

    @Test
    void refusesOnceSpentUntilMidnightUtc() {
        var budget = new DailyBudget(new BigDecimal("1"), clock);
        budget.record(new BigDecimal("0.6"));
        assertThatCode(budget::checkAvailable).doesNotThrowAnyException();
        budget.record(new BigDecimal("0.4"));

        // 10:00 UTC -> minuit : 14 h
        assertThatThrownBy(budget::checkAvailable)
                .isInstanceOfSatisfying(ExtractionQuotaExceededException.class,
                        e -> assertThat(e.retryAfterSeconds()).isBetween(14 * 3600L, 14 * 3600L + 2));
    }

    @Test
    void startsAgainAtMidnightUtc() {
        var budget = new DailyBudget(new BigDecimal("1"), clock);
        budget.record(new BigDecimal("1"));
        clock.now = Instant.parse("2026-11-03T23:59:59Z");
        assertThatThrownBy(budget::checkAvailable).isInstanceOf(ExtractionQuotaExceededException.class);

        clock.now = Instant.parse("2026-11-04T00:00:00Z");
        assertThatCode(budget::checkAvailable).doesNotThrowAnyException();
    }

    @Test
    void rejectsANonPositiveBudget() {
        assertThatThrownBy(() -> new DailyBudget(BigDecimal.ZERO, clock)).isInstanceOf(IllegalArgumentException.class);
    }

    private static final class MutableClock extends Clock {
        Instant now = Instant.parse("2026-11-03T10:00:00Z");

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
