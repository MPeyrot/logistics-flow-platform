package fr.michael.logistics.shipment.application;

/** Identifiant inconnu, mauvais mot de passe ou compte désactivé : volontairement indiscernables. */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Invalid username or password");
    }
}
