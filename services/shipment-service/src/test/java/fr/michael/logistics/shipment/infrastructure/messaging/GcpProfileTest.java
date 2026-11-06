package fr.michael.logistics.shipment.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** Propriétés de journalisation résolues avec ou sans le profil {@code gcp}, sans démarrer l'application. */
class GcpProfileTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer());

    @Test
    void sansProfilLesJournauxRestentEnTexte() {
        runner.run(context -> assertThat(context.getEnvironment().getProperty("logging.structured.format.console")).isNull());
    }

    @Test
    void profilGcpEcritDuJsonAvecLeNiveauDansSeverity() {
        runner.withPropertyValues("spring.profiles.active=gcp").run(context -> {
            var env = context.getEnvironment();
            assertThat(env.getProperty("logging.structured.format.console")).isEqualTo("logstash");
            assertThat(env.getProperty("logging.structured.json.rename.level")).isEqualTo("severity");
        });
    }
}
