package fr.michael.logistics.ai.infrastructure.web;

import fr.michael.logistics.ai.application.ExtractionQuota;
import fr.michael.logistics.ai.application.DailyBudget;
import java.math.BigDecimal;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class QuotaConfig {

    @Bean
    ExtractionQuota extractionQuota(@Value("${ai.extraction.max-per-hour}") int maxPerHour) {
        return new ExtractionQuota(maxPerHour, Clock.systemUTC());
    }

    @Bean
    DailyBudget dailyBudget(@Value("${ai.extraction.daily-budget-usd:}") String dailyBudgetUsd) {
        return new DailyBudget(parseBudget(dailyBudgetUsd), Clock.systemUTC());
    }

    private static BigDecimal parseBudget(String raw) {
        String message = "AI_DAILY_BUDGET_USD must be a number of dollars, 0 or more (0 refuses every extraction), got '"
                + raw + "'";
        if (raw == null || raw.isBlank()) {
            throw new IllegalStateException(message);
        }
        try {
            BigDecimal value = new BigDecimal(raw.trim());
            if (value.signum() < 0) {
                throw new IllegalStateException(message);
            }
            return value;
        } catch (NumberFormatException e) {
            throw new IllegalStateException(message, e);
        }
    }
}
