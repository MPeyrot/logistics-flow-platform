package fr.michael.logistics.ai.application;

/** Le fournisseur du modèle est injoignable ou a répondu en erreur. */
public class ExtractionFailedException extends RuntimeException {

    public ExtractionFailedException(String message) {
        super(message);
    }

    public ExtractionFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
