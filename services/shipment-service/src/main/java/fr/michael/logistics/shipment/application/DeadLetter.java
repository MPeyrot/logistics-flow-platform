package fr.michael.logistics.shipment.application;

import java.time.Instant;

/**
 * Message de {@code scan-events.DLT} tel qu'il a été écrit : clé et valeur brutes (pas forcément du JSON valide),
 * classe et message de l'exception qui l'y a envoyé. {@code timestamp} : date d'écriture dans le DLT.
 */
public record DeadLetter(int partition, long offset, Instant timestamp, String key, String value,
        String exceptionClass, String exceptionMessage) {

    public DeadLetterPosition position() {
        return new DeadLetterPosition(partition, offset);
    }
}
