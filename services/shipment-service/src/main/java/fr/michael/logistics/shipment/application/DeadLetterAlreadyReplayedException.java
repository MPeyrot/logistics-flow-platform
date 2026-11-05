package fr.michael.logistics.shipment.application;

public class DeadLetterAlreadyReplayedException extends RuntimeException {

    public DeadLetterAlreadyReplayedException(DeadLetterPosition position) {
        super("Message at partition %d, offset %d of the dead letter topic was already replayed"
                .formatted(position.partition(), position.offset()));
    }
}
