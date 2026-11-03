package fr.michael.logistics.shipment.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zaxxer.hikari.HikariDataSource;
import fr.michael.logistics.shipment.TestAccounts;
import fr.michael.logistics.shipment.TestcontainersConfiguration;
import fr.michael.logistics.shipment.application.AccountService;
import fr.michael.logistics.shipment.application.AuthenticationService;
import fr.michael.logistics.shipment.application.InvalidCredentialsException;
import fr.michael.logistics.shipment.application.PasswordHasher;
import fr.michael.logistics.shipment.domain.Role;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

/** La comparaison BCrypt (~100 ms) ne doit pas tenir une connexion du pool : une rafale de connexions le viderait. */
@SpringBootTest
@Import({TestcontainersConfiguration.class, LoginConnectionTest.RecordingHasherConfiguration.class})
class LoginConnectionTest {

    /** Connexions actives du pool relevées à chaque comparaison de mot de passe. */
    static final List<Integer> ACTIVE_CONNECTIONS_DURING_MATCH = new ArrayList<>();

    @Autowired
    AuthenticationService authentication;

    @Autowired
    AccountService accounts;

    @BeforeEach
    void clear() {
        synchronized (ACTIVE_CONNECTIONS_DURING_MATCH) {
            ACTIVE_CONNECTIONS_DURING_MATCH.clear();
        }
    }

    @Test
    void passwordIsComparedWithoutHoldingAConnection() {
        TestAccounts.ensure(accounts, "pool-operator", Role.OPERATOR, null);
        clear();

        authentication.issueToken("pool-operator", TestAccounts.PASSWORD);
        assertThatThrownBy(() -> authentication.issueToken("pool-unknown-user", "whatever password"))
                .isInstanceOf(InvalidCredentialsException.class);

        assertThat(ACTIVE_CONNECTIONS_DURING_MATCH).containsExactly(0, 0);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class RecordingHasherConfiguration {

        @Bean
        @Primary
        PasswordHasher recordingPasswordHasher(ObjectProvider<DataSource> dataSource) {
            PasswordHasher bcrypt = new BcryptPasswordHasher();
            return new PasswordHasher() {
                @Override
                public String hash(String rawPassword) {
                    return bcrypt.hash(rawPassword);
                }

                @Override
                public boolean matches(String rawPassword, String passwordHash) {
                    int active;
                    try {
                        active = dataSource.getObject().unwrap(HikariDataSource.class)
                                .getHikariPoolMXBean().getActiveConnections();
                    } catch (SQLException e) {
                        throw new IllegalStateException(e);
                    }
                    synchronized (ACTIVE_CONNECTIONS_DURING_MATCH) {
                        ACTIVE_CONNECTIONS_DURING_MATCH.add(active);
                    }
                    return bcrypt.matches(rawPassword, passwordHash);
                }
            };
        }
    }
}
