package fr.michael.logistics.shipment.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.kafka.clients.admin.NewTopic;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** Configuration Kafka produite, sans broker : propriétés résolues avec ou sans le profil {@code confluent}. */
class ConfluentProfileTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(DeadLetterTopicConfig.class, ConfluentCredentialsConfig.class, KafkaPropertiesConfig.class);

    @Test
    void sansProfilKafkaResteEnClairAvecUneReplique() {
        runner.run(context -> {
            var admin = context.getBean(KafkaProperties.class).buildAdminProperties();
            assertThat(admin).doesNotContainKey("security.protocol").doesNotContainKey("sasl.jaas.config");
            assertThat(context.getBean(NewTopic.class).replicationFactor()).isEqualTo((short) 1);
        });
    }

    @Test
    void profilConfluentActiveSaslSslEtTroisRepliques() {
        runner.withPropertyValues("spring.profiles.active=confluent", "KAFKA_API_KEY=ma-cle", "KAFKA_API_SECRET=mon-secret")
                .run(context -> {
                    var producer = context.getBean(KafkaProperties.class).buildProducerProperties();
                    assertThat(producer).containsEntry("security.protocol", "SASL_SSL")
                            .containsEntry("sasl.mechanism", "PLAIN");
                    assertThat(producer.get("sasl.jaas.config").toString())
                            .contains("PlainLoginModule required")
                            .contains("username=\"ma-cle\"")
                            .contains("password=\"mon-secret\"");
                    assertThat(context.getBean(NewTopic.class).replicationFactor()).isEqualTo((short) 3);
                });
    }

    @Test
    void profilConfluentSansCleRefuseDeDemarrer() {
        runner.withPropertyValues("spring.profiles.active=confluent", "KAFKA_API_SECRET=mon-secret")
                .run(context -> assertThat(context).hasFailed().getFailure()
                        .hasStackTraceContaining("KAFKA_API_KEY"));
    }

    @Test
    void profilConfluentSansSecretRefuseDeDemarrer() {
        runner.withPropertyValues("spring.profiles.active=confluent", "KAFKA_API_KEY=ma-cle")
                .run(context -> assertThat(context).hasFailed().getFailure()
                        .hasStackTraceContaining("KAFKA_API_SECRET"));
    }

    /** Les propriétés seules, sans KafkaAdmin : il chercherait un broker au démarrage. */
    @EnableConfigurationProperties(KafkaProperties.class)
    static class KafkaPropertiesConfig {}
}
