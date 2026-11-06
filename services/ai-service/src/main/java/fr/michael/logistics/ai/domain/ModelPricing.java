package fr.michael.logistics.ai.domain;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Tarif d'un modèle. Certains modèles (Claude Haiku 5.5) facturent une requête entière avec une seconde
 * grille dès que son prompt dépasse un seuil : {@code longContext}, absent pour les autres.
 */
public record ModelPricing(RateCard rates, LongContext longContext) {

    /** Grille appliquée à toute la requête quand son prompt compte plus de {@code thresholdTokens} jetons. */
    public record LongContext(long thresholdTokens, RateCard rates) {

        public LongContext {
            if (thresholdTokens <= 0) {
                throw new IllegalArgumentException("thresholdTokens must be positive");
            }
            Objects.requireNonNull(rates, "rates");
        }
    }

    public ModelPricing {
        Objects.requireNonNull(rates, "rates");
    }

    /** Tarif sans seconde grille. */
    public ModelPricing(RateCard rates) {
        this(rates, null);
    }

    /** Coût en dollars, arrondi au millionième. */
    public BigDecimal costOf(TokenUsage usage) {
        boolean longPrompt = longContext != null && usage.promptTokens() > longContext.thresholdTokens();
        return (longPrompt ? longContext.rates() : rates).costOf(usage);
    }
}
