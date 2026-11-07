package fr.michael.logistics.ingestion.application;

import java.time.Duration;

/** Un autre import tourne encore dans cette instance après l'attente permise. */
public class ImportBusyException extends RuntimeException {

    private final Duration retryAfter;

    public ImportBusyException(Duration retryAfter) {
        super("Another import is already running");
        this.retryAfter = retryAfter;
    }

    /** Délai conseillé avant de réessayer. */
    public Duration retryAfter() {
        return retryAfter;
    }
}
