package fr.michael.logistics.shipment.application;

/** Port sortant : confie un document (PDF ou image) au service d'extraction. */
public interface DeliveryNoteReader {

    /**
     * @throws UnsupportedDocumentException si le fichier n'est ni un PDF ni une image acceptée
     * @throws UnreadableDeliveryNoteException si aucun bon de livraison exploitable n'a été trouvé
     * @throws ExtractionServiceUnreachableException si la connexion au service d'extraction a échoué (rien n'est parti)
     * @throws ExtractionServiceUnavailableException si le service d'extraction est en erreur ou ne répond pas à temps
     */
    DeliveryNoteReading read(byte[] content, String filename);
}
