package fr.michael.logistics.ai.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/** Tarif d'un modèle en dollars par million de jetons. */
public record ModelPricing(
        BigDecimal inputPerMillion,
        BigDecimal outputPerMillion,
        BigDecimal cacheWritePerMillion,
        BigDecimal cacheReadPerMillion) {

    private static final BigDecimal MILLION = BigDecimal.valueOf(1_000_000);

    public ModelPricing {
        Objects.requireNonNull(inputPerMillion, "inputPerMillion");
        Objects.requireNonNull(outputPerMillion, "outputPerMillion");
        Objects.requireNonNull(cacheWritePerMillion, "cacheWritePerMillion");
        Objects.requireNonNull(cacheReadPerMillion, "cacheReadPerMillion");
    }

    /** Coût en dollars, arrondi au millionième. */
    public BigDecimal costOf(TokenUsage usage) {
        return inputPerMillion.multiply(BigDecimal.valueOf(usage.inputTokens()))
                .add(outputPerMillion.multiply(BigDecimal.valueOf(usage.outputTokens())))
                .add(cacheWritePerMillion.multiply(BigDecimal.valueOf(usage.cacheWriteTokens())))
                .add(cacheReadPerMillion.multiply(BigDecimal.valueOf(usage.cacheReadTokens())))
                .divide(MILLION, 6, RoundingMode.HALF_UP);
    }
}
