package fr.michael.logistics.shipment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.michael.logistics.shipment.application.AccountService.BootstrapOutcome;
import fr.michael.logistics.shipment.domain.Role;
import fr.michael.logistics.shipment.domain.UserAccount;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AccountServiceTest {

    private final InMemoryAccounts accounts = new InMemoryAccounts();
    private final AccountService service = new AccountService(accounts, new PrefixHasher(),
            Clock.fixed(Instant.parse("2026-11-03T10:00:00Z"), ZoneOffset.UTC));

    @Test
    void bootstrapCreatesFirstAdmin() {
        assertThat(service.bootstrapAdmin("root", "a long password")).isEqualTo(BootstrapOutcome.ADMIN_CREATED);

        UserAccount admin = accounts.findByUsername("root").orElseThrow();
        assertThat(admin.role()).isEqualTo(Role.ADMIN);
        assertThat(admin.passwordHash()).isEqualTo("hashed:a long password");
    }

    @Test
    void bootstrapLeavesExistingAdminAlone() {
        service.create("first-admin", "a long password", Role.ADMIN, null);

        assertThat(service.bootstrapAdmin("root", "a long password")).isEqualTo(BootstrapOutcome.ADMIN_EXISTS);
        assertThat(accounts.findByUsername("root")).isEmpty();
    }

    @Test
    void bootstrapWithoutCredentialsReportsMissingAdmin() {
        assertThat(service.bootstrapAdmin(null, null)).isEqualTo(BootstrapOutcome.NO_ADMIN);
        assertThat(service.bootstrapAdmin("root", "")).isEqualTo(BootstrapOutcome.NO_ADMIN);
        assertThat(accounts.existsByRole(Role.ADMIN)).isFalse();
    }

    @Test
    void bootstrapRejectsShortPassword() {
        assertThatThrownBy(() -> service.bootstrapAdmin("root", "short"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void usernameIsUniqueWhateverTheCase() {
        service.create("Alice", "a long password", Role.OPERATOR, null);

        assertThatThrownBy(() -> service.create("alice", "a long password", Role.OPERATOR, null))
                .isInstanceOf(DuplicateUsernameException.class);
    }

    @Test
    void reactivateRestoresADeactivatedAccount() {
        UserAccount account = service.create("carol", "a long password", Role.OPERATOR, null);
        service.deactivate(account.id());

        assertThat(service.reactivate(account.id()).active()).isTrue();
        assertThat(accounts.findById(account.id()).orElseThrow().active()).isTrue();
    }

    @Test
    void reactivatingAnActiveAccountChangesNothing() {
        UserAccount account = service.create("dave", "a long password", Role.OPERATOR, null);

        assertThat(service.reactivate(account.id())).isEqualTo(account);
    }

    @Test
    void reactivatingAnUnknownAccountFails() {
        assertThatThrownBy(() -> service.reactivate(UUID.randomUUID())).isInstanceOf(AccountNotFoundException.class);
    }

    private static final class PrefixHasher implements PasswordHasher {
        @Override
        public String hash(String rawPassword) {
            return "hashed:" + rawPassword;
        }

        @Override
        public boolean matches(String rawPassword, String passwordHash) {
            return passwordHash.equals(hash(rawPassword));
        }
    }

    private static final class InMemoryAccounts implements UserAccountRepository {
        private final List<UserAccount> accounts = new ArrayList<>();

        @Override
        public UserAccount save(UserAccount account) {
            accounts.removeIf(a -> a.id().equals(account.id()));
            accounts.add(account);
            return account;
        }

        @Override
        public Optional<UserAccount> findByUsername(String username) {
            return accounts.stream().filter(a -> a.username().equalsIgnoreCase(username)).findFirst();
        }

        @Override
        public boolean existsByUsername(String username) {
            return findByUsername(username).isPresent();
        }

        @Override
        public boolean existsByRole(Role role) {
            return accounts.stream().anyMatch(a -> a.role() == role);
        }

        @Override
        public Optional<UserAccount> findById(UUID id) {
            return accounts.stream().filter(a -> a.id().equals(id)).findFirst();
        }

        @Override
        public int countActiveAdminsLocking() {
            return (int) accounts.stream().filter(a -> a.role() == Role.ADMIN && a.active()).count();
        }
    }
}
