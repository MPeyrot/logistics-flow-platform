package fr.michael.logistics.shipment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.michael.logistics.shipment.domain.Role;
import fr.michael.logistics.shipment.domain.UserAccount;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AuthenticationServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-03T10:00:00Z"), ZoneOffset.UTC);

    private final FlakyAccounts accounts = new FlakyAccounts(
            UserAccount.create("paul", "hashed:a long password", Role.OPERATOR, null, CLOCK.instant()));
    private final LoginThrottle throttle = new LoginThrottle(5, Duration.ofMinutes(15), Duration.ofMinutes(15), CLOCK);
    private final AuthenticationService service = new AuthenticationService(accounts, new PrefixHasher(),
            claims -> "signed:" + claims.subject(), throttle, CLOCK);

    /** Base indisponible : les tentatives n'ont rien vérifié, elles ne comptent pas comme des échecs. */
    @Test
    void attemptsInterruptedByAnUnexpectedErrorAreNotCountedAsFailures() {
        accounts.down = true;
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> service.issueToken("paul", "a long password"))
                    .isInstanceOf(IllegalStateException.class);
        }

        accounts.down = false;
        assertThat(service.issueToken("paul", "a long password").value()).isEqualTo("signed:paul");
    }

    @Test
    void wrongPasswordsStillBlockTheUsername() {
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> service.issueToken("paul", "wrong password"))
                    .isInstanceOf(InvalidCredentialsException.class);
        }

        assertThatThrownBy(() -> service.issueToken("paul", "a long password"))
                .isInstanceOf(LoginBlockedException.class);
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

    /** Un seul compte ; {@code down} simule une base indisponible. */
    private static final class FlakyAccounts implements UserAccountRepository {
        private final UserAccount account;
        private boolean down;

        FlakyAccounts(UserAccount account) {
            this.account = account;
        }

        @Override
        public Optional<UserAccount> findByUsername(String username) {
            if (down) {
                throw new IllegalStateException("database unavailable");
            }
            return account.username().equalsIgnoreCase(username) ? Optional.of(account) : Optional.empty();
        }

        @Override
        public UserAccount save(UserAccount account) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean existsByUsername(String username) {
            return findByUsername(username).isPresent();
        }

        @Override
        public boolean existsByRole(Role role) {
            return account.role() == role;
        }

        @Override
        public Optional<UserAccount> findById(UUID id) {
            return Optional.empty();
        }

        @Override
        public int countActiveAdminsLocking() {
            return 0;
        }

        @Override
        public java.util.List<UserAccount> findAllOrderedByUsername() {
            return java.util.List.of();
        }
    }
}
