package fr.michael.logistics.shipment.application;

/** Trop d'échecs de connexion pour cet identifiant : il est bloqué pour un temps. */
public class LoginBlockedException extends RuntimeException {

    private final long retryAfterSeconds;

    public LoginBlockedException(long retryAfterSeconds) {
        super("Too many failed login attempts, retry later");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
