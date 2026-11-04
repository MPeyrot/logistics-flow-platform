package fr.michael.logistics.shipment.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import fr.michael.logistics.shipment.application.AccountService;
import fr.michael.logistics.shipment.application.AccountService.BootstrapOutcome;
import fr.michael.logistics.shipment.application.PasswordHasher;
import fr.michael.logistics.shipment.application.UserAccountRepository;
import fr.michael.logistics.shipment.domain.Role;
import fr.michael.logistics.shipment.domain.UserAccount;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Deux instances démarrent ensemble sur une base vide (#30) : vraie base PostgreSQL, vrai index unique, transaction
 * de {@link AccountService#create} comprise. Les deux amorçages vérifient l'absence d'ADMIN et d'identifiant avant
 * que l'un ou l'autre n'écrive : le second se heurte à l'index unique.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import(ConcurrentBootstrapAdminTest.Config.class)
class ConcurrentBootstrapAdminTest {

    @Autowired
    AccountService accounts;

    @Autowired
    SpringDataUserAccountRepository jpa;

    @Test
    void twoConcurrentBootstrapsOnAnEmptyDatabaseBothSucceedWithASingleAdmin() throws Exception {
        assertThat(jpa.count()).isZero();

        ExecutorService instances = Executors.newFixedThreadPool(2);
        try {
            Future<BootstrapOutcome> first = instances.submit(() -> accounts.bootstrapAdmin("root", "a long password"));
            Future<BootstrapOutcome> second = instances.submit(() -> accounts.bootstrapAdmin("root", "a long password"));

            assertThat(List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(BootstrapOutcome.ADMIN_CREATED, BootstrapOutcome.ADMIN_CREATED_ELSEWHERE);
        } finally {
            instances.shutdownNow();
        }
        assertThat(jpa.findAll()).singleElement().satisfies(account -> {
            assertThat(account.toDomain().role()).isEqualTo(Role.ADMIN);
            assertThat(account.toDomain().username()).isEqualTo("root");
        });
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Config {

        @Bean
        @ServiceConnection
        PostgreSQLContainer postgresContainer() {
            return new PostgreSQLContainer(DockerImageName.parse("postgres:17"));
        }

        @Bean
        UserAccountRepository userAccountRepository(SpringDataUserAccountRepository jpa) {
            return new BothCheckBeforeWriting(new JpaUserAccountRepository(jpa));
        }

        /** Bean Spring : {@link AccountService#create} garde sa transaction, comme en production. */
        @Bean
        AccountService accountService(UserAccountRepository repository) {
            return new AccountService(repository, new PlainHasher(), Clock.systemUTC());
        }
    }

    /**
     * Fait attendre chaque amorçage, une fois l'absence d'ADMIN vérifiée et juste avant de vérifier l'identifiant,
     * que l'autre en soit au même point : aucun des deux n'a encore écrit, les deux tentent la création.
     */
    private static final class BothCheckBeforeWriting implements UserAccountRepository {

        private final UserAccountRepository delegate;
        private final CyclicBarrier bothChecked = new CyclicBarrier(2);

        BothCheckBeforeWriting(UserAccountRepository delegate) {
            this.delegate = delegate;
        }

        @Override
        public boolean existsByUsername(String username) {
            try {
                bothChecked.await(10, TimeUnit.SECONDS);
            } catch (Exception e) {
                throw new IllegalStateException("the other bootstrap never reached the username check", e);
            }
            return delegate.existsByUsername(username);
        }

        @Override
        public UserAccount save(UserAccount account) {
            return delegate.save(account);
        }

        @Override
        public Optional<UserAccount> findByUsername(String username) {
            return delegate.findByUsername(username);
        }

        @Override
        public boolean existsByRole(Role role) {
            return delegate.existsByRole(role);
        }

        @Override
        public Optional<UserAccount> findById(UUID id) {
            return delegate.findById(id);
        }

        @Override
        public int countActiveAdminsLocking() {
            return delegate.countActiveAdminsLocking();
        }
    }

    private static final class PlainHasher implements PasswordHasher {
        @Override
        public String hash(String rawPassword) {
            return "plain:" + rawPassword;
        }

        @Override
        public boolean matches(String rawPassword, String passwordHash) {
            return passwordHash.equals(hash(rawPassword));
        }
    }
}
