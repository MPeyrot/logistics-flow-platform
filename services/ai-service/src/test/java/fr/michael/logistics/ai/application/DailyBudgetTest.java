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
        budget.record(budget.checkAvailable(), new BigDecimal("0.6"));
        assertThatCode(budget::checkAvailable).doesNotThrowAnyException();
        budget.record(budget.checkAvailable(), new BigDecimal("0.4"));

        // 10:00 UTC -> minuit : 14 h
        assertThatThrownBy(budget::checkAvailable)
                .isInstanceOfSatisfying(ExtractionQuotaExceededException.class,
                        e -> assertThat(e.retryAfterSeconds()).isBetween(14 * 3600L, 14 * 3600L + 2));
    }

    @Test
    void startsAgainAtMidnightUtc() {
        var budget = new DailyBudget(new BigDecimal("1"), clock);
        budget.record(budget.checkAvailable(), new BigDecimal("1"));
        clock.now = Instant.parse("2026-11-03T23:59:59Z");
        assertThatThrownBy(budget::checkAvailable).isInstanceOf(ExtractionQuotaExceededException.class);

        clock.now = Instant.parse("2026-11-04T00:00:00Z");
        assertThatCode(budget::checkAvailable).doesNotThrowAnyException();
    }

    /** Un appel contrôlé le 3 à 23:59:59 et terminé le 4 est imputé au 3 : le budget du 4 reste intact. */
    @Test
    void chargesACallToTheDayItWasCheckedOn() {
        var budget = new DailyBudget(new BigDecimal("1"), clock);
        clock.now = Instant.parse("2026-11-03T23:59:59Z");
        var checkedOn = budget.checkAvailable();

        clock.now = Instant.parse("2026-11-04T00:00:05Z");
        budget.record(checkedOn, new BigDecimal("1"));

        assertThatCode(budget::checkAvailable).doesNotThrowAnyException();
    }

    @Test
    void aZeroBudgetRefusesEveryCallUntilMidnightUtc() {
        var budget = new DailyBudget(BigDecimal.ZERO, clock);
        assertThatThrownBy(budget::checkAvailable)
                .isInstanceOfSatisfying(ExtractionQuotaExceededException.class,
                        e -> assertThat(e.retryAfterSeconds()).isBetween(14 * 3600L, 14 * 3600L + 2));
    }

    @Test
    void rejectsANegativeBudget() {
        assertThatThrownBy(() -> new DailyBudget(new BigDecimal("-0.01"), clock))
                .isInstanceOf(IllegalArgumentException.class);
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
