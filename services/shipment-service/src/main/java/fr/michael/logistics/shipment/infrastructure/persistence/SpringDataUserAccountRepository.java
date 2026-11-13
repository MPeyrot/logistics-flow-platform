package fr.michael.logistics.shipment.infrastructure.persistence;

import fr.michael.logistics.shipment.domain.Role;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface SpringDataUserAccountRepository extends JpaRepository<UserAccountJpaEntity, UUID> {

    /** lower() comme l'index unique user_account_username_lower. */
    @Query("select a from UserAccountJpaEntity a where lower(a.username) = lower(?1)")
    Optional<UserAccountJpaEntity> findByUsernameIgnoringCase(String username);

    boolean existsByRole(Role role);

    List<UserAccountJpaEntity> findAllByOrderByUsernameAsc();

    /** SELECT … FOR UPDATE : une transaction concurrente attend, puis relit les lignes. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<UserAccountJpaEntity> findByRoleAndActiveTrue(Role role);
}
