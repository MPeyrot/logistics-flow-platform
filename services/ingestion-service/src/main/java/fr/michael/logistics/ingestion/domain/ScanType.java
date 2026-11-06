package fr.michael.logistics.ingestion.domain;

/** Types de scan, alignés sur contracts/scan-event.v2.schema.json. */
public enum ScanType {
    PICKED_UP,
    ARRIVED_AT_SITE,
    OUT_FOR_DELIVERY,
    DELIVERED,
    DELIVERY_FAILED
}
