package fr.michael.logistics.shipment.application;

public class DeadLetterNotFoundException extends RuntimeException {

    public DeadLetterNotFoundException(DeadLetterPosition position) {
        super("No message at partition %d, offset %d of the dead letter topic"
                .formatted(position.partition(), position.offset()));
    }
}
