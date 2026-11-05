package fr.michael.logistics.shipment.application;

import java.time.Instant;

/** Message du DLT et date de son rejeu ({@code null} s'il n'a pas été rejoué). */
public record DeadLetterEntry(DeadLetter letter, Instant replayedAt) {

    public boolean replayed() {
        return replayedAt != null;
    }
}
