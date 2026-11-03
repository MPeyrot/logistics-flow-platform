package fr.michael.logistics.shipment.application;

/** Trop d'extractions : la limite de l'utilisateur est atteinte, ou ai-service a lui-même répondu 429. */
public class ExtractionRateLimitedException extends RuntimeException {

    private final Long retryAfterSeconds;

    /** @param retryAfterSeconds délai conseillé, ou null si inconnu (429 d'ai-service sans {@code Retry-After}) */
    public ExtractionRateLimitedException(String message, Long retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public Long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
