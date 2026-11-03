package fr.michael.logistics.shipment.application;

/** Désactiver ce compte ne laisserait plus aucun ADMIN actif : personne ne pourrait plus administrer le service. */
public class LastActiveAdminException extends RuntimeException {

    public LastActiveAdminException() {
        super("The last active ADMIN cannot be deactivated");
    }
}
