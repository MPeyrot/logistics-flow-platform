package fr.michael.logistics.shipment.application;

public class ShipmentNotFoundException extends RuntimeException {

    public ShipmentNotFoundException(String reference) {
        super("No shipment with reference " + reference);
    }
}
