package fr.michael.logistics.ingestion.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import fr.michael.logistics.ingestion.application.ScanPublicationException;
import fr.michael.logistics.ingestion.domain.ScanEvent;
import fr.michael.logistics.ingestion.domain.ScanType;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import tools.jackson.databind.json.JsonMapper;

/** Kafka injoignable : une publication échoue en ~10 s au plus avec la configuration par défaut. */
class KafkaProducerTimeoutsTest {

    private static final ScanEvent EVENT =
            new ScanEvent(UUID.randomUUID(), "REF-1", ScanType.PICKED_UP, "Nantes", Instant.parse("2026-11-07T08:00:00Z"));

    @Test
    void anUnreachableKafkaFailsWithinAboutTenSecondsWithTheDefaultConfiguration() {
        new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withUserConfiguration(KafkaPropertiesConfig.class)
                .withPropertyValues("spring.kafka.bootstrap-servers=localhost:1")
                .run(context -> {
                    var factory = new DefaultKafkaProducerFactory<String, String>(
                            context.getBean(KafkaProperties.class).buildProducerProperties());
                    var publisher = new KafkaScanPublisher(new KafkaTemplate<>(factory), JsonMapper.builder().build());

                    assertTimeoutPreemptively(Duration.ofSeconds(14), () ->
                            assertThatThrownBy(() -> publisher.publishAll(List.of(EVENT)))
                                    .isInstanceOf(ScanPublicationException.class));
                    factory.destroy();
                });
    }

    @EnableConfigurationProperties(KafkaProperties.class)
    static class KafkaPropertiesConfig {}
}
