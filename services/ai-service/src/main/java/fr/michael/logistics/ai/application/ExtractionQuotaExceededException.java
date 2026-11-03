package fr.michael.logistics.ai.application;

public class ExtractionQuotaExceededException extends RuntimeException {

    private final long retryAfterSeconds;

    public ExtractionQuotaExceededException(long retryAfterSeconds) {
        super("Extraction limit reached");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
