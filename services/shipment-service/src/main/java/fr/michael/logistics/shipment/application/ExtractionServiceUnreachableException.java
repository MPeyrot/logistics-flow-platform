package fr.michael.logistics.shipment.application;

/**
 * La connexion au service d'extraction a échoué (connexion refusée, hôte injoignable) : aucune requête n'est partie,
 * donc aucun appel Claude n'a pu être facturé. Une expiration de lecture n'en est pas une : la requête était partie.
 */
public class ExtractionServiceUnreachableException extends ExtractionServiceUnavailableException {

    public ExtractionServiceUnreachableException(String message, Throwable cause) {
        super(message, cause);
    }
}
