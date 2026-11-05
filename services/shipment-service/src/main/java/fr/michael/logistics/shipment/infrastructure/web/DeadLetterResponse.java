package fr.michael.logistics.shipment.infrastructure.web;

import fr.michael.logistics.shipment.application.DeadLetter;
import fr.michael.logistics.shipment.application.DeadLetterEntry;
import java.time.Instant;

/** {@code timestamp} : écriture dans le DLT ; {@code value} : message brut, pas forcément du JSON valide. */
record DeadLetterResponse(int partition, long offset, Instant timestamp, String key, String value,
        String exceptionClass, String exceptionMessage, boolean replayed, Instant replayedAt) {

    static DeadLetterResponse from(DeadLetterEntry entry) {
        DeadLetter letter = entry.letter();
        return new DeadLetterResponse(letter.partition(), letter.offset(), letter.timestamp(), letter.key(),
                letter.value(), letter.exceptionClass(), letter.exceptionMessage(), entry.replayed(),
                entry.replayedAt());
    }
}
