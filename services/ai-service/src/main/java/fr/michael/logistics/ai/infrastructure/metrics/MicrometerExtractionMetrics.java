package fr.michael.logistics.ai.infrastructure.metrics;

import fr.michael.logistics.ai.application.ExtractionMetrics;
import fr.michael.logistics.ai.domain.TokenUsage;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Métriques Micrometer des appels facturés, cumulées depuis le démarrage de l'instance :
 * <ul>
 *   <li>{@code ai.claude.cost} (dollars) : nombre d'appels facturés, coût total et maximal ; étiquettes
 *       {@code model} et {@code outcome} ({@code extracted} ou {@code unreadable}) ;</li>
 *   <li>{@code ai.claude.tokens} : jetons facturés ; étiquettes {@code model} et {@code type}
 *       ({@code input}, {@code output}, {@code cache_write}, {@code cache_read}).</li>
 * </ul>
 */
@Component
class MicrometerExtractionMetrics implements ExtractionMetrics {

    private final DistributionSummary extractedCost;
    private final DistributionSummary unreadableCost;
    private final Counter inputTokens;
    private final Counter outputTokens;
    private final Counter cacheWriteTokens;
    private final Counter cacheReadTokens;

    MicrometerExtractionMetrics(MeterRegistry registry, @Value("${ai.claude.model}") String model) {
        this.extractedCost = cost(registry, model, "extracted");
        this.unreadableCost = cost(registry, model, "unreadable");
        this.inputTokens = tokens(registry, model, "input");
        this.outputTokens = tokens(registry, model, "output");
        this.cacheWriteTokens = tokens(registry, model, "cache_write");
        this.cacheReadTokens = tokens(registry, model, "cache_read");
    }

    @Override
    public void recordBilledCall(TokenUsage usage, BigDecimal costUsd, boolean extracted) {
        (extracted ? extractedCost : unreadableCost).record(costUsd.doubleValue());
        inputTokens.increment(usage.inputTokens());
        outputTokens.increment(usage.outputTokens());
        cacheWriteTokens.increment(usage.cacheWriteTokens());
        cacheReadTokens.increment(usage.cacheReadTokens());
    }

    private static DistributionSummary cost(MeterRegistry registry, String model, String outcome) {
        return DistributionSummary.builder("ai.claude.cost")
                .description("Coût des appels facturés à l'API Claude")
                .baseUnit("usd")
                .tag("model", model)
                .tag("outcome", outcome)
                .register(registry);
    }

    private static Counter tokens(MeterRegistry registry, String model, String type) {
        return Counter.builder("ai.claude.tokens")
                .description("Jetons facturés par l'API Claude")
                .baseUnit("tokens")
                .tag("model", model)
                .tag("type", type)
                .register(registry);
    }
}
