package fr.michael.logistics.shipment.infrastructure.messaging;

/** Message Kafka illisible ou hors contrat : inutile de le rejouer, il part directement en DLT. */
class InvalidMessageException extends RuntimeException {

    InvalidMessageException(String message, Throwable cause) {
        super(message, cause);
    }
}
