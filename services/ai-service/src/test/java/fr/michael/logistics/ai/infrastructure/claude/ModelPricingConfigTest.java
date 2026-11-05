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
            "claude-sonnet-5-5, 2,    10,   2.50,  0.10",
            "claude-opus-5-5,   4,    20,   5,     0.20"})
    void pricingFollowsConfiguredModel(String model, String input, String output, String cacheWrite,
            String cacheRead) {
        // Modèle fixé explicitement : sinon AI_CLAUDE_MODEL du shell choisirait le modèle.
        context.withPropertyValues("ai.claude.model=" + model).run(started -> {
            var pricing = started.getBean(ModelPricing.class);
            assertThat(pricing.inputPerMillion()).isEqualByComparingTo(new BigDecimal(input));
            assertThat(pricing.outputPerMillion()).isEqualByComparingTo(new BigDecimal(output));
            assertThat(pricing.cacheWritePerMillion()).isEqualByComparingTo(new BigDecimal(cacheWrite));
            assertThat(pricing.cacheReadPerMillion()).isEqualByComparingTo(new BigDecimal(cacheRead));
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
