package fr.michael.logistics.shipment.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class UserAccountTest {

    private static final Instant NOW = Instant.parse("2026-11-03T10:00:00Z");
    private static final String HASH = "$2a$10$hash";

    @Test
    void newAccountIsActive() {
        UserAccount account = UserAccount.create("alice", HASH, Role.OPERATOR, null, NOW);

        assertThat(account.active()).isTrue();
        assertThat(account.id()).isNotNull();
    }

    @Test
    void scannerNeedsASite() {
        assertThatThrownBy(() -> UserAccount.create("scan1", HASH, Role.SCANNER, null, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> UserAccount.create("scan1", HASH, Role.SCANNER, " ", NOW))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(UserAccount.create("scan1", HASH, Role.SCANNER, "NANTES", NOW).site()).isEqualTo("NANTES");
    }

    @Test
    void onlyAScannerHasASite() {
        assertThatThrownBy(() -> UserAccount.create("alice", HASH, Role.OPERATOR, "NANTES", NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> UserAccount.create("root", HASH, Role.ADMIN, "NANTES", NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void usernameHasThreeToFiftyCharacters() {
        assertThatThrownBy(() -> UserAccount.create("ab", HASH, Role.OPERATOR, null, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> UserAccount.create("a".repeat(51), HASH, Role.OPERATOR, null, NOW))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(UserAccount.create("abc", HASH, Role.OPERATOR, null, NOW).username()).isEqualTo("abc");
        assertThat(UserAccount.create("a".repeat(50), HASH, Role.OPERATOR, null, NOW)).isNotNull();
    }

    @Test
    void passwordNeedsAtLeastTwelveCharacters() {
        assertThatThrownBy(() -> UserAccount.requireAcceptablePassword("elevenchars"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> UserAccount.requireAcceptablePassword(null))
                .isInstanceOf(IllegalArgumentException.class);

        UserAccount.requireAcceptablePassword("twelve chars");
    }

    @Test
    void deactivatedAccountKeepsItsIdentity() {
        UserAccount account = UserAccount.create("alice", HASH, Role.OPERATOR, null, NOW);

        UserAccount deactivated = account.deactivate();

        assertThat(deactivated.active()).isFalse();
        assertThat(deactivated.id()).isEqualTo(account.id());
        assertThat(deactivated.username()).isEqualTo("alice");
    }
}
