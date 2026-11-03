package fr.michael.logistics.shipment.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Compte d'un utilisateur de la plateforme. Le mot de passe n'est connu que sous forme hachée.
 * L'unicité de l'identifiant (sans distinction de casse) est garantie par la base.
 */
public record UserAccount(
        UUID id,
        String username,
        String passwordHash,
        Role role,
        String site,
        boolean active,
        Instant createdAt) {

    public static final int USERNAME_MIN_LENGTH = 3;
    public static final int USERNAME_MAX_LENGTH = 50;
    public static final int PASSWORD_MIN_LENGTH = 12;

    public UserAccount {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(createdAt, "createdAt");
        if (username == null || username.isBlank()
                || username.length() < USERNAME_MIN_LENGTH || username.length() > USERNAME_MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "username must have " + USERNAME_MIN_LENGTH + " to " + USERNAME_MAX_LENGTH + " characters");
        }
        if (passwordHash == null || passwordHash.isBlank()) {
            throw new IllegalArgumentException("passwordHash must not be blank");
        }
        boolean hasSite = site != null && !site.isBlank();
        if (role == Role.SCANNER && !hasSite) {
            throw new IllegalArgumentException("a SCANNER account needs a site");
        }
        if (role != Role.SCANNER && site != null) {
            throw new IllegalArgumentException("only a SCANNER account has a site");
        }
    }

    public static UserAccount create(String username, String passwordHash, Role role, String site, Instant now) {
        return new UserAccount(UUID.randomUUID(), username, passwordHash, role, site, true, now);
    }

    /** Longueur minimale seulement, sans règle de complexité. */
    public static void requireAcceptablePassword(String rawPassword) {
        if (rawPassword == null || rawPassword.length() < PASSWORD_MIN_LENGTH) {
            throw new IllegalArgumentException("password must have at least " + PASSWORD_MIN_LENGTH + " characters");
        }
    }

    public UserAccount deactivate() {
        return new UserAccount(id, username, passwordHash, role, site, false, createdAt);
    }
}
