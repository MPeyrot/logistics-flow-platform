package fr.michael.logistics.shipment.infrastructure.persistence;

import fr.michael.logistics.shipment.domain.Role;
import fr.michael.logistics.shipment.domain.UserAccount;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_account")
class UserAccountJpaEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String username;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    private String site;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected UserAccountJpaEntity() {
    }

    static UserAccountJpaEntity from(UserAccount account) {
        UserAccountJpaEntity entity = new UserAccountJpaEntity();
        entity.id = account.id();
        entity.username = account.username();
        entity.passwordHash = account.passwordHash();
        entity.role = account.role();
        entity.site = account.site();
        entity.active = account.active();
        entity.createdAt = account.createdAt();
        return entity;
    }

    UserAccount toDomain() {
        return new UserAccount(id, username, passwordHash, role, site, active, createdAt);
    }
}
