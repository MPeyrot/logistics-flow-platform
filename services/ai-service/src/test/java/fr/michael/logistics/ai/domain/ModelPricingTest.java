package fr.michael.logistics.ai.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ModelPricingTest {

    private final ModelPricing opus = new ModelPricing(
            new BigDecimal("4.00"), new BigDecimal("20.00"), new BigDecimal("5.00"), new BigDecimal("0.20"));

    @Test
    void pricesEachTokenKindSeparately() {
        // 1000*4 + 100*20 + 500*5 + 2000*0,20 = 8900 micro-dollars
        BigDecimal cost = opus.costOf(new TokenUsage(1000, 100, 500, 2000));
        assertThat(cost).isEqualByComparingTo("0.008900");
    }

    @Test
    void cacheReadIsCheaperThanFreshInput() {
        BigDecimal fresh = opus.costOf(new TokenUsage(10_000, 0, 0, 0));
        BigDecimal cached = opus.costOf(new TokenUsage(0, 0, 0, 10_000));
        assertThat(cached).isLessThan(fresh);
    }

    @Test
    void zeroUsageCostsNothing() {
        assertThat(opus.costOf(new TokenUsage(0, 0, 0, 0))).isEqualByComparingTo("0");
    }
}
