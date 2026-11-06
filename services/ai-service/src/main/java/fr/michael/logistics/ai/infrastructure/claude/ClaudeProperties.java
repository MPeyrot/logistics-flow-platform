package fr.michael.logistics.ai.infrastructure.claude;

import fr.michael.logistics.ai.domain.ModelPricing;
import fr.michael.logistics.ai.domain.RateCard;
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

    /**
     * Tarif d'un modèle, en dollars par million de jetons ; la table {@code pricing} est indexée par modèle.
     * {@code longContext} : seconde grille au-delà d'un seuil de prompt, pour les modèles qui en ont une.
     * Seule {@link RateCard} contrôle que les quatre prix sont là.
     */
    public record Pricing(BigDecimal input, BigDecimal output, BigDecimal cacheWrite, BigDecimal cacheRead,
            LongContext longContext) {

        /** Grille de base, ou {@code null} s'il manque un prix. */
        RateCard rates() {
            return RateCard.ofOrNull(input, output, cacheWrite, cacheRead);
        }
    }

    /** Grille appliquée à toute la requête quand son prompt dépasse {@code thresholdTokens} jetons. */
    public record LongContext(Long thresholdTokens, BigDecimal input, BigDecimal output, BigDecimal cacheWrite,
            BigDecimal cacheRead) {

        /** Grille longue, ou {@code null} s'il manque le seuil ou un prix. */
        ModelPricing.LongContext toDomain() {
            var rates = RateCard.ofOrNull(input, output, cacheWrite, cacheRead);
            return thresholdTokens == null || rates == null ? null
                    : new ModelPricing.LongContext(thresholdTokens, rates);
        }
    }

    boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }
}
