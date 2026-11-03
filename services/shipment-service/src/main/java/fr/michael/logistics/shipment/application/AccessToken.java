package fr.michael.logistics.shipment.application;

import java.time.Instant;

public record AccessToken(String value, Instant expiresAt) {

    /** Jamais la valeur du jeton dans les journaux. */
    @Override
    public String toString() {
        return "AccessToken[expiresAt=" + expiresAt + "]";
    }
}
