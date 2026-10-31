package fr.michael.logistics.shipment.application;

/** Le service d'extraction est injoignable ou a répondu en erreur. */
public class ExtractionServiceUnavailableException extends RuntimeException {

    public ExtractionServiceUnavailableException(String message) {
        super(message);
    }

    public ExtractionServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
