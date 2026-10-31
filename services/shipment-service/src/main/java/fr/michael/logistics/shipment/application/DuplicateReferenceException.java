package fr.michael.logistics.shipment.application;

public class DuplicateReferenceException extends RuntimeException {

    public DuplicateReferenceException(String reference) {
        super("A shipment with reference " + reference + " already exists");
    }
}
