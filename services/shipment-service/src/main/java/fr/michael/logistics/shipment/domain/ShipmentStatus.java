package fr.michael.logistics.shipment.domain;

/** Statuts d'une expédition. Transitions : {@link Shipment#applyScan}. DELIVERED est terminal. */
public enum ShipmentStatus {
    CREATED,
    IN_TRANSIT,
    OUT_FOR_DELIVERY,
    DELIVERED,
    DELIVERY_FAILED
}
