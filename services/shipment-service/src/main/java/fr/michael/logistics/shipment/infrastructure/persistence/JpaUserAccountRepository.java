package fr.michael.logistics.shipment.infrastructure.persistence;

import fr.michael.logistics.shipment.application.DuplicateUsernameException;
import fr.michael.logistics.shipment.application.UserAccountRepository;
import fr.michael.logistics.shipment.domain.Role;
import fr.michael.logistics.shipment.domain.UserAccount;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

@Repository
class JpaUserAccountRepository implements UserAccountRepository {

    /** Index unique de V3__create_user_account.sql. */
    private static final String UNIQUE_USERNAME_INDEX = "user_account_username_lower";

    private final SpringDataUserAccountRepository jpa;

    JpaUserAccountRepository(SpringDataUserAccountRepository jpa) {
        this.jpa = jpa;
    }

    /** Écrit tout de suite : deux créations simultanées du même identifiant donnent 409, pas 500 au commit. */
    @Override
    public UserAccount save(UserAccount account) {
        try {
            return jpa.saveAndFlush(UserAccountJpaEntity.from(account)).toDomain();
        } catch (DataIntegrityViolationException e) {
            if (String.valueOf(e.getMostSpecificCause().getMessage()).contains(UNIQUE_USERNAME_INDEX)) {
                throw new DuplicateUsernameException(account.username());
            }
            throw e;
        }
    }

    @Override
    public Optional<UserAccount> findByUsername(String username) {
        if (username == null) {
            return Optional.empty();
        }
        return jpa.findByUsernameIgnoringCase(username).map(UserAccountJpaEntity::toDomain);
    }

    @Override
    public boolean existsByUsername(String username) {
        return findByUsername(username).isPresent();
    }

    @Override
    public boolean existsByRole(Role role) {
        return jpa.existsByRole(role);
    }

    @Override
    public Optional<UserAccount> findById(UUID id) {
        return jpa.findById(id).map(UserAccountJpaEntity::toDomain);
    }

    @Override
    public int countActiveAdminsLocking() {
        return jpa.findByRoleAndActiveTrue(Role.ADMIN).size();
    }
}
