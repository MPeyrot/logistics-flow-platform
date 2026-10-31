package fr.michael.logistics.ingestion.domain;

/** Types provisoires, alignés sur contracts/scan-event.v1.schema.json. */
public enum ScanType {
    PICKED_UP,
    ARRIVED_AT_SITE,
    DELIVERED
}
