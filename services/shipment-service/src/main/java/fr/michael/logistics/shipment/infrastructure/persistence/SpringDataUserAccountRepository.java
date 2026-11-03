package fr.michael.logistics.shipment.infrastructure.persistence;

import fr.michael.logistics.shipment.domain.Role;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface SpringDataUserAccountRepository extends JpaRepository<UserAccountJpaEntity, UUID> {

    /** lower() comme l'index unique user_account_username_lower. */
    @Query("select a from UserAccountJpaEntity a where lower(a.username) = lower(?1)")
    Optional<UserAccountJpaEntity> findByUsernameIgnoringCase(String username);

    boolean existsByRole(Role role);
}
