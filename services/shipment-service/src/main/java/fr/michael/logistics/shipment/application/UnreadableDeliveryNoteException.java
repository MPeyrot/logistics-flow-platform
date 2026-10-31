package fr.michael.logistics.shipment.application;

/** Aucun bon de livraison exploitable n'a été trouvé dans le document. */
public class UnreadableDeliveryNoteException extends RuntimeException {

    public UnreadableDeliveryNoteException(String message) {
        super(message);
    }

    public UnreadableDeliveryNoteException(String message, Throwable cause) {
        super(message, cause);
    }
}
