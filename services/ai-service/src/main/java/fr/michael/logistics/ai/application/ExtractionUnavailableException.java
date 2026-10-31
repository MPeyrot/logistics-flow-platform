package fr.michael.logistics.ai.application;

/** Le service d'extraction n'est pas configuré (clé d'API absente). */
public class ExtractionUnavailableException extends RuntimeException {

    public ExtractionUnavailableException(String message) {
        super(message);
    }

    public ExtractionUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
