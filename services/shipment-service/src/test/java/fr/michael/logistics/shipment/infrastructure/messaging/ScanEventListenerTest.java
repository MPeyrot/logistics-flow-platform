package fr.michael.logistics.shipment.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import fr.michael.logistics.shipment.TestcontainersConfiguration;
import fr.michael.logistics.shipment.application.ShipmentRepository;
import fr.michael.logistics.shipment.application.ShipmentService;
import fr.michael.logistics.shipment.domain.ShipmentStatus;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.testcontainers.kafka.KafkaContainer;

@SpringBootTest(properties = {"shipment.kafka.retry-interval-ms=100", "shipment.kafka.retry-attempts=2"})
@Import(TestcontainersConfiguration.class)
class ScanEventListenerTest {

    @Autowired
    KafkaTemplate<String, String> kafka;

    @Autowired
    KafkaContainer kafkaContainer;

    @Autowired
    ShipmentService shipments;

    @Autowired
    ShipmentRepository repository;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void appliesScansReceivedOnKafka() {
        String ref = newShipment();

        send(ref, UUID.randomUUID(), "PICKED_UP");

        awaitStatus(ref, ShipmentStatus.IN_TRANSIT);
    }

    @Test
    void processesARedeliveredEventOnlyOnce() {
        String ref = newShipment();
        UUID pickup = UUID.randomUUID();

        UUID arrival = UUID.randomUUID();

        send(ref, pickup, "PICKED_UP");
        send(ref, pickup, "PICKED_UP");
        send(ref, arrival, "ARRIVED_AT_SITE");

        // Même clé, même partition : le scan suivant enregistré prouve que le doublon a été traité.
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(storedCount(arrival)).isEqualTo(1));
        assertThat(storedCount(pickup)).isEqualTo(1);
        awaitStatus(ref, ShipmentStatus.IN_TRANSIT);
    }

    @Test
    void sendsMalformedMessageToDeadLetterTopic() {
        String garbage = "not json " + UUID.randomUUID();

        kafka.send("scan-events", "bad-key", garbage);

        assertThat(readDeadLetters(garbage)).isEqualTo(garbage);
    }

    @Test
    void sendsForbiddenTransitionToDeadLetterTopic() {
        String ref = newShipment();
        String payload = message(ref, UUID.randomUUID(), "DELIVERED");

        kafka.send("scan-events", ref, payload);

        assertThat(readDeadLetters(payload)).isEqualTo(payload);
        assertThat(repository.findByReference(ref)).get()
                .extracting(s -> s.status()).isEqualTo(ShipmentStatus.CREATED);
    }

    private String newShipment() {
        String ref = "KAFKA-" + UUID.randomUUID();
        shipments.create(ref, "Nantes", "Lyon");
        return ref;
    }

    private void send(String ref, UUID eventId, String type) {
        kafka.send("scan-events", ref, message(ref, eventId, type));
    }

    private static String message(String ref, UUID eventId, String type) {
        return """
                {"eventId":"%s","shipmentReference":"%s","type":"%s","site":"Nantes","occurredAt":"2026-10-31T10:00:00Z"}"""
                .formatted(eventId, ref, type);
    }

    private Integer storedCount(UUID eventId) {
        return jdbc.queryForObject("SELECT count(*) FROM scan_event WHERE event_id = ?", Integer.class, eventId);
    }

    private void awaitStatus(String ref, ShipmentStatus expected) {
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(repository.findByReference(ref)).get()
                        .extracting(s -> s.status()).isEqualTo(expected));
    }

    private String readDeadLetters(String expectedValue) {
        Map<String, Object> config = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaContainer.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "dlt-test-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(config)) {
            consumer.subscribe(List.of("scan-events.DLT"));
            long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
            while (System.nanoTime() < deadline) {
                for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(500))) {
                    if (expectedValue.equals(record.value())) {
                        return record.value();
                    }
                }
            }
        }
        throw new AssertionError("Message not found on scan-events.DLT");
    }
}
