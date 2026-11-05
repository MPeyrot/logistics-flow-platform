package fr.michael.logistics.shipment.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import fr.michael.logistics.shipment.TestcontainersConfiguration;
import fr.michael.logistics.shipment.application.DeadLetter;
import fr.michael.logistics.shipment.application.DeadLetterPosition;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.testcontainers.kafka.KafkaContainer;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class KafkaScanEventDeadLettersTest {

    @Autowired
    ConsumerFactory<?, ?> consumers;

    @Autowired
    KafkaTemplate<String, String> kafka;

    @Autowired
    KafkaContainer kafkaContainer;

    @Test
    void anAbsentDeadLetterTopicIsEmptyAndIsNotCreatedByReadingIt() throws Exception {
        String absent = "absent-" + UUID.randomUUID();
        KafkaScanEventDeadLetters deadLetters = new KafkaScanEventDeadLetters(
                consumers.getConfigurationProperties(), kafka, absent, "scan-events", Duration.ofSeconds(10));
        List<DeadLetter> visited = new ArrayList<>();

        assertThat(deadLetters.latest(100)).isEmpty();
        assertThat(deadLetters.find(new DeadLetterPosition(0, 0))).isEmpty();
        deadLetters.forEach(visited::add);

        assertThat(visited).isEmpty();
        try (AdminClient admin = AdminClient.create(
                Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaContainer.getBootstrapServers()))) {
            assertThat(admin.listTopics().names().get()).doesNotContain(absent);
        }
    }
}
