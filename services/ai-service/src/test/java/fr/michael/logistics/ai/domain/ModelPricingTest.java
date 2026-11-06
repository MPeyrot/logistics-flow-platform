package fr.michael.logistics.ai.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ModelPricingTest {

    private final ModelPricing opus = new ModelPricing(new RateCard(
            new BigDecimal("4.00"), new BigDecimal("20.00"), new BigDecimal("5.00"), new BigDecimal("0.20")));

    // Grilles de Claude Haiku 5.5 : la seconde s'applique au-delà de 100 000 jetons de prompt.
    private final ModelPricing haiku = new ModelPricing(
            new RateCard(new BigDecimal("0.10"), new BigDecimal("0.50"), new BigDecimal("0.125"),
                    new BigDecimal("0.01")),
            new ModelPricing.LongContext(100_000, new RateCard(new BigDecimal("0.50"), new BigDecimal("2.50"),
                    new BigDecimal("0.625"), new BigDecimal("0.05"))));

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

    @Test
    void promptAtThresholdUsesStandardRates() {
        // 100 000 jetons pile, cache compris : 99 000*0,10 + 1000*0,01 + 1000*0,50 = 10 410 micro-dollars
        BigDecimal cost = haiku.costOf(new TokenUsage(99_000, 1000, 0, 1000));
        assertThat(cost).isEqualByComparingTo("0.010410");
    }

    @Test
    void promptAboveThresholdUsesLongContextRatesForWholeRequest() {
        // 100 001 jetons de prompt dont 1 lu dans le cache : la seconde grille vaut aussi pour la sortie.
        // 100 000*0,50 + 1*0,05 + 1000*2,50 = 52 500,05 micro-dollars
        BigDecimal cost = haiku.costOf(new TokenUsage(100_000, 1000, 0, 1));
        assertThat(cost).isEqualByComparingTo("0.052500");
    }

    @Test
    void cacheTokensCountTowardThreshold() {
        BigDecimal mostlyCached = haiku.costOf(new TokenUsage(1000, 0, 0, 100_000));
        // 1000*0,50 + 100 000*0,05 = 5500 micro-dollars (grille standard : 1100)
        assertThat(mostlyCached).isEqualByComparingTo("0.005500");
    }

    @Test
    void longContextThresholdMustBePositive() {
        var rates = new RateCard(BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE);
        assertThatThrownBy(() -> new ModelPricing.LongContext(0, rates))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
