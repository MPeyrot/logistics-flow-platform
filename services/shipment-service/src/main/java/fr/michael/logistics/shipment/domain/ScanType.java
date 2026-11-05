package fr.michael.logistics.shipment.domain;

/** Types de scan d'une expédition. */
public enum ScanType {
    PICKED_UP,
    ARRIVED_AT_SITE,
    OUT_FOR_DELIVERY,
    DELIVERED,
    DELIVERY_FAILED
}
