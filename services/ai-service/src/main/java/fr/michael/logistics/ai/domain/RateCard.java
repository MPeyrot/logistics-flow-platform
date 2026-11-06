package fr.michael.logistics.ai.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/** Grille de prix en dollars par million de jetons, un prix par sorte de jeton. */
public record RateCard(
        BigDecimal inputPerMillion,
        BigDecimal outputPerMillion,
        BigDecimal cacheWritePerMillion,
        BigDecimal cacheReadPerMillion) {

    private static final BigDecimal MILLION = BigDecimal.valueOf(1_000_000);

    public RateCard {
        Objects.requireNonNull(inputPerMillion, "inputPerMillion");
        Objects.requireNonNull(outputPerMillion, "outputPerMillion");
        Objects.requireNonNull(cacheWritePerMillion, "cacheWritePerMillion");
        Objects.requireNonNull(cacheReadPerMillion, "cacheReadPerMillion");
    }

    /** Coût en dollars, arrondi au millionième. */
    BigDecimal costOf(TokenUsage usage) {
        return inputPerMillion.multiply(BigDecimal.valueOf(usage.inputTokens()))
                .add(outputPerMillion.multiply(BigDecimal.valueOf(usage.outputTokens())))
                .add(cacheWritePerMillion.multiply(BigDecimal.valueOf(usage.cacheWriteTokens())))
                .add(cacheReadPerMillion.multiply(BigDecimal.valueOf(usage.cacheReadTokens())))
                .divide(MILLION, 6, RoundingMode.HALF_UP);
    }
}
