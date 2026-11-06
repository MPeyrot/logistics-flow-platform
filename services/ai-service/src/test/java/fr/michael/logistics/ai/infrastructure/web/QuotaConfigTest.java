package fr.michael.logistics.ai.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.michael.logistics.ai.application.DailyBudget;
import fr.michael.logistics.ai.application.ExtractionQuotaExceededException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** AI_DAILY_BUDGET_USD : 0 coupe les extractions ; négatif, vide ou non numérique : démarrage refusé, message clair (#72). */
class QuotaConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(QuotaConfig.class)
            .withPropertyValues("ai.extraction.max-per-hour=100");

    @Test
    void zeroIsAcceptedAndRefusesEveryExtraction() {
        runner.withPropertyValues("ai.extraction.daily-budget-usd=0").run(context -> {
            assertThat(context).hasNotFailed();
            DailyBudget budget = context.getBean(DailyBudget.class);
            assertThatThrownBy(budget::checkAvailable).isInstanceOf(ExtractionQuotaExceededException.class);
        });
    }

    @Test
    void aPositiveAmountIsAccepted() {
        runner.withPropertyValues("ai.extraction.daily-budget-usd=0.5")
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void negativeBlankAndNonNumericValuesRefuseToStartWithAClearMessage() {
        for (String value : new String[] {"-1", "", "  ", "abc"}) {
            runner.withPropertyValues("ai.extraction.daily-budget-usd=" + value).run(context -> {
                assertThat(context).as("valeur '%s'", value).hasFailed();
                assertThat(messages(context.getStartupFailure())).contains("AI_DAILY_BUDGET_USD");
            });
        }
    }

    private static String messages(Throwable t) {
        StringBuilder sb = new StringBuilder();
        for (; t != null; t = t.getCause()) {
            sb.append(t.getMessage()).append('\n');
        }
        return sb.toString();
    }
}
