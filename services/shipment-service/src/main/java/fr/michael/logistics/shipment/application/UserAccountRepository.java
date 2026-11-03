package fr.michael.logistics.shipment.application;

import fr.michael.logistics.shipment.domain.Role;
import fr.michael.logistics.shipment.domain.UserAccount;
import java.util.Optional;
import java.util.UUID;

public interface UserAccountRepository {

    UserAccount save(UserAccount account);

    /** Recherche sans distinction de casse. */
    Optional<UserAccount> findByUsername(String username);

    /** Sans distinction de casse. */
    boolean existsByUsername(String username);

    boolean existsByRole(Role role);

    Optional<UserAccount> findById(UUID id);

    /** Nombre d'ADMIN actifs, dont les lignes restent verrouillées jusqu'à la fin de la transaction. */
    int countActiveAdminsLocking();
}
