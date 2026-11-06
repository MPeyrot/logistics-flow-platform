package fr.michael.logistics.shipment.application;

/** Place d'un message dans {@code scan-events.DLT}. */
public record DeadLetterPosition(int partition, long offset) {
}
