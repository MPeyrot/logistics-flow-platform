package fr.michael.logistics.ai.application;

import fr.michael.logistics.ai.domain.SourceDocument;

/** Port sortant : lit un document et en tire un bon de livraison. */
public interface DeliveryNoteExtractor {

    /**
     * @throws ExtractionUnavailableException si le service d'extraction n'est pas configuré
     * @throws ExtractionFailedException si le fournisseur est injoignable ou répond en erreur
     * @throws UnreadableDocumentException si le document ne donne pas de bon de livraison exploitable
     */
    ModelExtraction extract(SourceDocument document);
}
