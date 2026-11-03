package fr.michael.logistics.shipment.application;

import fr.michael.logistics.shipment.domain.Role;
import fr.michael.logistics.shipment.domain.UserAccount;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {

    public enum BootstrapOutcome {
        /** Un ADMIN existait déjà : rien n'est fait. */
        ADMIN_EXISTS,
        /** Le premier ADMIN vient d'être créé. */
        ADMIN_CREATED,
        /** Aucun ADMIN et aucun identifiant d'amorçage : personne ne peut administrer le service. */
        NO_ADMIN
    }

    private final UserAccountRepository repository;
    private final PasswordHasher passwordHasher;
    private final Clock clock;

    public AccountService(UserAccountRepository repository, PasswordHasher passwordHasher, Clock clock) {
        this.repository = repository;
        this.passwordHasher = passwordHasher;
        this.clock = clock;
    }

    @Transactional
    public UserAccount create(String username, String rawPassword, Role role, String site) {
        UserAccount.requireAcceptablePassword(rawPassword);
        if (username != null && repository.existsByUsername(username)) {
            throw new DuplicateUsernameException(username);
        }
        UserAccount account = UserAccount.create(username, passwordHasher.hash(rawPassword), role, site, clock.instant());
        return repository.save(account);
    }

    /** Crée le premier ADMIN si aucun n'existe et que l'identifiant et le mot de passe sont fournis. */
    @Transactional
    public BootstrapOutcome bootstrapAdmin(String username, String rawPassword) {
        if (repository.existsByRole(Role.ADMIN)) {
            return BootstrapOutcome.ADMIN_EXISTS;
        }
        if (username == null || username.isBlank() || rawPassword == null || rawPassword.isBlank()) {
            return BootstrapOutcome.NO_ADMIN;
        }
        create(username, rawPassword, Role.ADMIN, null);
        return BootstrapOutcome.ADMIN_CREATED;
    }
}
