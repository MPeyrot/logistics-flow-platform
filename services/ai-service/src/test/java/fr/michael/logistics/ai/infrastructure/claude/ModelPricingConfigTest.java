package fr.michael.logistics.ai.infrastructure.claude;

import static org.assertj.core.api.Assertions.assertThat;

import fr.michael.logistics.ai.domain.ModelPricing;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Le tarif suit le modèle choisi (table de application.properties), et le service refuse de démarrer
 * quand le modèle n'a pas de tarif : sinon costUsd serait calculé avec le tarif d'un autre modèle (#10).
 */
class ModelPricingConfigTest {

    // Lit le vrai application.properties : ce sont ses tarifs qu'on vérifie.
    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(ClaudeConfig.class);

    @ParameterizedTest
    @CsvSource({
            "claude-haiku-5-5,  0.10, 0.50, 0.125, 0.01",
            "claude-sonnet-5-5, 2,    10,   2.50,  0.20",
            "claude-opus-5-5,   4,    20,   5,     0.20"})
    void pricingFollowsConfiguredModel(String model, String input, String output, String cacheWrite,
            String cacheRead) {
        // Modèle fixé explicitement : sinon AI_CLAUDE_MODEL du shell choisirait le modèle.
        context.withPropertyValues("ai.claude.model=" + model).run(started -> {
            var rates = started.getBean(ModelPricing.class).rates();
            assertThat(rates.inputPerMillion()).isEqualByComparingTo(new BigDecimal(input));
            assertThat(rates.outputPerMillion()).isEqualByComparingTo(new BigDecimal(output));
            assertThat(rates.cacheWritePerMillion()).isEqualByComparingTo(new BigDecimal(cacheWrite));
            assertThat(rates.cacheReadPerMillion()).isEqualByComparingTo(new BigDecimal(cacheRead));
        });
    }

    /** Haiku 5.5 seul a une seconde grille (prompt de plus de 100 000 jetons) ; Sonnet et Opus n'en ont pas. */
    @Test
    void haikuHasLongContextRates() {
        context.withPropertyValues("ai.claude.model=claude-haiku-5-5").run(started -> {
            var longContext = started.getBean(ModelPricing.class).longContext();
            assertThat(longContext.thresholdTokens()).isEqualTo(100_000);
            assertThat(longContext.rates().inputPerMillion()).isEqualByComparingTo("0.50");
            assertThat(longContext.rates().outputPerMillion()).isEqualByComparingTo("2.50");
            assertThat(longContext.rates().cacheWritePerMillion()).isEqualByComparingTo("0.625");
            assertThat(longContext.rates().cacheReadPerMillion()).isEqualByComparingTo("0.05");
        });
        context.withPropertyValues("ai.claude.model=claude-opus-5-5")
                .run(started -> assertThat(started.getBean(ModelPricing.class).longContext()).isNull());
    }

    @Test
    void startupFailsForIncompleteLongContextPricing() {
        context.withPropertyValues("ai.claude.model=claude-partial-2",
                        "ai.claude.pricing.claude-partial-2.input=1",
                        "ai.claude.pricing.claude-partial-2.output=1",
                        "ai.claude.pricing.claude-partial-2.cache-write=1",
                        "ai.claude.pricing.claude-partial-2.cache-read=1",
                        "ai.claude.pricing.claude-partial-2.long-context.input=2")
                .run(started -> {
                    assertThat(started).hasFailed();
                    assertThat(started.getStartupFailure()).rootCause()
                            .hasMessageContaining("claude-partial-2.long-context");
                });
    }

    @Test
    void startupFailsForModelWithoutPricing() {
        context.withPropertyValues("ai.claude.model=claude-unknown-9").run(started -> {
            assertThat(started).hasFailed();
            assertThat(started.getStartupFailure()).rootCause()
                    .hasMessageContaining("claude-unknown-9")
                    .hasMessageContaining("ai.claude.pricing");
        });
    }

    @Test
    void startupFailsForIncompletePricing() {
        context.withPropertyValues("ai.claude.model=claude-partial-1", "ai.claude.pricing.claude-partial-1.input=1")
                .run(started -> {
                    assertThat(started).hasFailed();
                    assertThat(started.getStartupFailure()).rootCause().hasMessageContaining("claude-partial-1");
                });
    }
}
