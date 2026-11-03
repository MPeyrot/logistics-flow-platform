package fr.michael.logistics.shipment.infrastructure.persistence;

import fr.michael.logistics.shipment.application.UserAccountRepository;
import fr.michael.logistics.shipment.domain.Role;
import fr.michael.logistics.shipment.domain.UserAccount;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
class JpaUserAccountRepository implements UserAccountRepository {

    private final SpringDataUserAccountRepository jpa;

    JpaUserAccountRepository(SpringDataUserAccountRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public UserAccount save(UserAccount account) {
        return jpa.save(UserAccountJpaEntity.from(account)).toDomain();
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
}
