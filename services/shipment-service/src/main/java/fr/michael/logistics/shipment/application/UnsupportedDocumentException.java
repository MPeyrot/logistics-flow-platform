package fr.michael.logistics.shipment.application;

/** Le fichier n'est ni un PDF ni une image acceptée. */
public class UnsupportedDocumentException extends RuntimeException {

    public UnsupportedDocumentException(String message) {
        super(message);
    }

    public UnsupportedDocumentException(String message, Throwable cause) {
        super(message, cause);
    }
}
