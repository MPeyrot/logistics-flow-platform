package fr.michael.logistics.ai.domain;

/**
 * Jetons facturés pour une requête. Comme dans l'API Claude, {@code inputTokens} exclut les jetons
 * lus ou écrits dans le cache de prompt, qui sont comptés à part.
 */
public record TokenUsage(long inputTokens, long outputTokens, long cacheWriteTokens, long cacheReadTokens) {

    public TokenUsage {
        if (inputTokens < 0 || outputTokens < 0 || cacheWriteTokens < 0 || cacheReadTokens < 0) {
            throw new IllegalArgumentException("token counts must not be negative");
        }
    }
}
