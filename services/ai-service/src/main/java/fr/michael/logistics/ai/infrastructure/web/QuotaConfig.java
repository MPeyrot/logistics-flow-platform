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
    DailyBudget dailyBudget(@Value("${ai.extraction.daily-budget-usd}") BigDecimal dailyBudgetUsd) {
        return new DailyBudget(dailyBudgetUsd, Clock.systemUTC());
    }
}
