package fr.michael.logistics.shipment.domain;

public class InvalidScanException extends RuntimeException {

    public InvalidScanException(Shipment shipment, ScanType type) {
        super("Scan " + type + " is not allowed for shipment " + shipment.reference()
                + " in status " + shipment.status());
    }
}
