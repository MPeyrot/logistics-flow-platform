package fr.michael.logistics.ai.infrastructure.claude;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Réglages de l'appel à l'API Claude. La clé vient de la variable d'environnement
 * {@code ANTHROPIC_API_KEY} (voir application.properties), jamais du dépôt.
 */
@ConfigurationProperties("ai.claude")
public record ClaudeProperties(
        String apiKey,
        String baseUrl,
        String model,
        String effort,
        long maxTokens,
        int maxRetries,
        Duration timeout,
        Map<String, Pricing> pricing) {

    /** Tarif d'un modèle, en dollars par million de jetons ; la table {@code pricing} est indexée par modèle. */
    public record Pricing(BigDecimal input, BigDecimal output, BigDecimal cacheWrite, BigDecimal cacheRead) {
    }

    boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }
}
