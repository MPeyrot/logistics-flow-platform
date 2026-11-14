package fr.michael.logistics.shipment.application;

/** Jetons consommés par la lecture d'un bon, tels que les rend ai-service. */
public record TokenUsage(long inputTokens, long outputTokens, long cacheWriteTokens, long cacheReadTokens) {
}
