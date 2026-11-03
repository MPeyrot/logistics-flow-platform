package fr.michael.logistics.shipment.infrastructure.web;

import fr.michael.logistics.shipment.domain.Role;
import fr.michael.logistics.shipment.domain.UserAccount;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Le site est obligatoire pour un SCANNER et interdit sinon : règle du domaine (UserAccount), 400 aussi.
 * Mot de passe limité à 72 octets UTF-8 : BCrypt ne lit pas au-delà.
 */
record CreateUserRequest(
        @NotBlank @Size(min = UserAccount.USERNAME_MIN_LENGTH, max = UserAccount.USERNAME_MAX_LENGTH) String username,
        @NotNull @Size(min = UserAccount.PASSWORD_MIN_LENGTH) @MaxUtf8Bytes(72) String password,
        @NotNull Role role,
        @Size(max = 100) String site) {

    /** Jamais le mot de passe dans les journaux. */
    @Override
    public String toString() {
        return "CreateUserRequest[username=" + username + ", role=" + role + ", site=" + site + "]";
    }
}
