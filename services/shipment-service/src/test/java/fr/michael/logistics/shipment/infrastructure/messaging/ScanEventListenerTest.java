package fr.michael.logistics.shipment.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import fr.michael.logistics.shipment.TestcontainersConfiguration;
import fr.michael.logistics.shipment.application.ShipmentRepository;
import fr.michael.logistics.shipment.application.ShipmentService;
import fr.michael.logistics.shipment.domain.ShipmentStatus;
import java.time.Duration;
import java.time.Instant;
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
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.testcontainers.kafka.KafkaContainer;

/** Backoff « expédition inconnue » raccourci : 200 ms, ×2, plafond 1 s, 4 s au total. */
@SpringBootTest(properties = {
        "spring.kafka.listener.auto-startup=true",
        "shipment.kafka.retry-interval-ms=100", "shipment.kafka.retry-attempts=2",
        "shipment.kafka.unknown-shipment.initial-interval=200ms",
        "shipment.kafka.unknown-shipment.multiplier=2",
        "shipment.kafka.unknown-shipment.max-interval=1s",
        "shipment.kafka.unknown-shipment.max-elapsed-time=4s"})
@DirtiesContext
@Import(TestcontainersConfiguration.class)
class ScanEventListenerTest {

    /** Les attentes cumulées atteignent au moins max-elapsed-time (4 s) avant l'abandon ; marge pour l'horloge. */
    private static final Duration MIN_UNKNOWN_SHIPMENT_WAIT = Duration.ofMillis(3_500);

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
    void followsAFailedDeliveryThenASecondAttemptUpToDelivery() {
        String ref = newShipment();
        UUID failure = UUID.randomUUID();

        send(ref, UUID.randomUUID(), "PICKED_UP");
        send(ref, UUID.randomUUID(), "OUT_FOR_DELIVERY");
        kafka.send("scan-events", ref, message(ref, failure, "DELIVERY_FAILED", "Destinataire absent"));
        awaitStatus(ref, ShipmentStatus.DELIVERY_FAILED);
        send(ref, UUID.randomUUID(), "ARRIVED_AT_SITE");
        send(ref, UUID.randomUUID(), "OUT_FOR_DELIVERY");
        send(ref, UUID.randomUUID(), "DELIVERED");

        awaitStatus(ref, ShipmentStatus.DELIVERED);
        assertThat(jdbc.queryForObject("SELECT reason FROM scan_event WHERE event_id = ?", String.class, failure))
                .isEqualTo("Destinataire absent");
    }

    @Test
    void stillReadsAMessageWrittenAgainstV1() {
        String ref = newShipment();
        UUID eventId = UUID.randomUUID();

        // Exemple de contracts/scan-event.v1.schema.json : ni OUT_FOR_DELIVERY, ni DELIVERY_FAILED, ni reason.
        kafka.send("scan-events", ref, """
                {"eventId":"%s","shipmentReference":"%s","type":"PICKED_UP","site":"Nantes","occurredAt":"2026-10-31T10:00:00Z"}"""
                .formatted(eventId, ref));

        awaitStatus(ref, ShipmentStatus.IN_TRANSIT);
        assertThat(jdbc.queryForObject("SELECT reason FROM scan_event WHERE event_id = ?", String.class, eventId))
                .isNull();
    }

    @Test
    void sendsReasonOnAnotherTypeToDeadLetterTopic() {
        String ref = newShipment();
        String payload = message(ref, UUID.randomUUID(), "PICKED_UP", "Destinataire absent");

        kafka.send("scan-events", ref, payload);

        assertThat(readDeadLetters(payload)).isEqualTo(payload);
        assertThat(repository.findByReference(ref)).get()
                .extracting(s -> s.status()).isEqualTo(ShipmentStatus.CREATED);
    }

    @Test
    void sendsMessageBreakingTheContractLimitsToDeadLetterTopic() {
        String ref = newShipment();
        String payload = """
                {"eventId":"%s","shipmentReference":"%s","type":"PICKED_UP","site":"%s","occurredAt":"2026-10-31T10:00:00Z"}"""
                .formatted(UUID.randomUUID(), ref, "N".repeat(101));

        kafka.send("scan-events", ref, payload);

        assertThat(readDeadLetters(payload)).isEqualTo(payload);
        assertThat(repository.findByReference(ref)).get()
                .extracting(s -> s.status()).isEqualTo(ShipmentStatus.CREATED);
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

    @Test
    void sendsScanOfUnknownShipmentToDeadLetterTopicAfterTheWholeBackOff() {
        String ref = "UNKNOWN-" + UUID.randomUUID();
        String payload = message(ref, UUID.randomUUID(), "PICKED_UP");
        long start = System.nanoTime();

        kafka.send("scan-events", ref, payload);

        assertThat(readDeadLetters(payload)).isEqualTo(payload);
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isGreaterThanOrEqualTo(MIN_UNKNOWN_SHIPMENT_WAIT);
    }

    @Test
    void appliesScanOfShipmentCreatedDuringTheBackOff() throws InterruptedException {
        appliesScansReceivedOnKafka(); // l'écouteur a rejoint le groupe : le scan suivant est lu tout de suite
        String ref = "KAFKA-" + UUID.randomUUID();

        send(ref, UUID.randomUUID(), "PICKED_UP");
        // Plusieurs tentatives échouent. Chacune coûte aussi ~0,5 s de relecture après le seek (fetch.max.wait.ms) :
        // 2 s dépasse le réglage des autres erreurs (100 ms × 2), qui aurait déjà envoyé le scan en DLT.
        Thread.sleep(2_000);
        shipments.create(ref, "Nantes", "Lyon", Instant.now().plus(Duration.ofDays(2)));

        awaitStatus(ref, ShipmentStatus.IN_TRANSIT);
    }

    @Test
    void scanOfAnotherShipmentOnTheSamePartitionWaitsForTheEndOfTheBackOff() {
        String known = newShipment();
        String unknown = "UNKNOWN-" + UUID.randomUUID();
        String unknownPayload = message(unknown, UUID.randomUUID(), "PICKED_UP");
        long start = System.nanoTime();

        kafka.send("scan-events", 0, unknown, unknownPayload);
        kafka.send("scan-events", 0, known, message(known, UUID.randomUUID(), "PICKED_UP"));

        awaitStatus(known, ShipmentStatus.IN_TRANSIT);
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isGreaterThanOrEqualTo(MIN_UNKNOWN_SHIPMENT_WAIT);
        assertThat(readDeadLetters(unknownPayload)).isEqualTo(unknownPayload);
    }

    private String newShipment() {
        String ref = "KAFKA-" + UUID.randomUUID();
        shipments.create(ref, "Nantes", "Lyon", Instant.now().plus(Duration.ofDays(2)));
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

    private static String message(String ref, UUID eventId, String type, String reason) {
        return """
                {"eventId":"%s","shipmentReference":"%s","type":"%s","site":"Nantes","occurredAt":"2026-10-31T10:00:00Z","reason":"%s"}"""
                .formatted(eventId, ref, type, reason);
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
