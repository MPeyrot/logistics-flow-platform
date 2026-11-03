package fr.michael.logistics.shipment.application;

public class DuplicateUsernameException extends RuntimeException {

    public DuplicateUsernameException(String username) {
        super("Username already exists: " + username);
    }
}
