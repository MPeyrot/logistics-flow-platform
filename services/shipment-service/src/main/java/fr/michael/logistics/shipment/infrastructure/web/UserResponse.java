package fr.michael.logistics.shipment.infrastructure.web;

import fr.michael.logistics.shipment.domain.Role;
import fr.michael.logistics.shipment.domain.UserAccount;
import java.time.Instant;
import java.util.UUID;

/** Jamais le mot de passe ni son empreinte. */
record UserResponse(UUID id, String username, Role role, String site, boolean active, Instant createdAt) {

    static UserResponse from(UserAccount account) {
        return new UserResponse(account.id(), account.username(), account.role(), account.site(),
                account.active(), account.createdAt());
    }
}
