package fr.michael.logistics.shipment.application;

import fr.michael.logistics.shipment.domain.Role;
import fr.michael.logistics.shipment.domain.UserAccount;
import java.util.Optional;

public interface UserAccountRepository {

    UserAccount save(UserAccount account);

    /** Recherche sans distinction de casse. */
    Optional<UserAccount> findByUsername(String username);

    /** Sans distinction de casse. */
    boolean existsByUsername(String username);

    boolean existsByRole(Role role);
}
