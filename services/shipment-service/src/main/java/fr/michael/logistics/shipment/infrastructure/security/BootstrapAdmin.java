package fr.michael.logistics.shipment.infrastructure.security;

import fr.michael.logistics.shipment.application.AccountService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Au démarrage, crée le premier ADMIN à partir de BOOTSTRAP_ADMIN_USERNAME et BOOTSTRAP_ADMIN_PASSWORD
 * si aucun n'existe. Sans ces variables et sans ADMIN, journalise un avertissement et laisse démarrer.
 */
@Component
class BootstrapAdmin implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapAdmin.class);

    private final AccountService accounts;
    private final String username;
    private final String password;

    BootstrapAdmin(AccountService accounts,
            @Value("${bootstrap.admin.username:}") String username,
            @Value("${bootstrap.admin.password:}") String password) {
        this.accounts = accounts;
        this.username = username;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        switch (accounts.bootstrapAdmin(username, password)) {
            case ADMIN_CREATED -> log.info("bootstrap: first ADMIN account created (username={})", username);
            case NO_ADMIN -> log.warn("bootstrap: no ADMIN account exists and BOOTSTRAP_ADMIN_USERNAME / "
                    + "BOOTSTRAP_ADMIN_PASSWORD are not set: nobody can administer accounts");
            case ADMIN_CREATED_ELSEWHERE -> log.info("bootstrap: first ADMIN account created concurrently by another "
                    + "instance (username={}), nothing to do", username);
            case ADMIN_EXISTS -> { }
        }
    }
}
