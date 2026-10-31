package fr.michael.logistics.shipment.application;

/** Port sortant : confie un document (PDF ou image) au service d'extraction. */
public interface DeliveryNoteReader {

    /**
     * @throws UnsupportedDocumentException si le fichier n'est ni un PDF ni une image acceptée
     * @throws UnreadableDeliveryNoteException si aucun bon de livraison exploitable n'a été trouvé
     * @throws ExtractionServiceUnavailableException si le service d'extraction est injoignable ou en erreur
     */
    DeliveryNoteReading read(byte[] content, String filename);
}
