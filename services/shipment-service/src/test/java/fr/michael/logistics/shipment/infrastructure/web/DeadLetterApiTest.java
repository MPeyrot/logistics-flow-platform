package fr.michael.logistics.shipment.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import fr.michael.logistics.shipment.TestAccounts;
import fr.michael.logistics.shipment.TestcontainersConfiguration;
import fr.michael.logistics.shipment.application.AccountService;
import fr.michael.logistics.shipment.application.ShipmentRepository;
import fr.michael.logistics.shipment.application.ShipmentService;
import fr.michael.logistics.shipment.domain.ShipmentStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.TopicDescription;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.kafka.KafkaContainer;

/**
 * Lecture et rejeu de scan-events.DLT. Contexte propre (backoff « expédition inconnue » raccourci) : le DLT ne
 * contient que les messages de cette classe. Chaque test crée l'expédition de son scan dès qu'il est en DLT, pour
 * que tout rejeu, même celui d'un autre test, s'applique et ne revienne pas en DLT.
 */
@SpringBootTest(properties = {
        "spring.kafka.listener.auto-startup=true", "shipment.kafka.dlt.outbox.interval=1s",
        "shipment.kafka.unknown-shipment.initial-interval=100ms",
        "shipment.kafka.unknown-shipment.multiplier=2",
        "shipment.kafka.unknown-shipment.max-interval=200ms",
        "shipment.kafka.unknown-shipment.max-elapsed-time=500ms"})
@AutoConfigureMockMvc
@DirtiesContext
@Import(TestcontainersConfiguration.class)
class DeadLetterApiTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    KafkaContainer kafkaContainer;

    @Autowired
    KafkaTemplate<String, String> kafka;

    @Autowired
    ShipmentService shipments;

    @Autowired
    ShipmentRepository repository;

    @Autowired
    AccountService accounts;

    @Test
    void declaresTheDeadLetterTopicWithThreePartitions() throws Exception {
        try (AdminClient admin = admin()) {
            TopicDescription dlt = admin.describeTopics(List.of("scan-events.DLT")).allTopicNames().get()
                    .get("scan-events.DLT");

            assertThat(dlt.partitions()).hasSize(3);
        }
    }

    @Test
    void listsAScanOfAnUnknownShipmentWithTheReasonOfItsFailure() throws Exception {
        String ref = "DLT-" + UUID.randomUUID();
        String payload = scan(ref, "PICKED_UP");

        kafka.send("scan-events", ref, payload);

        Map<String, Object> letter = awaitDeadLetter(payload);
        shipments.create(ref, "Nantes", "Lyon", Instant.now().plus(Duration.ofDays(2)));
        assertThat(letter).containsEntry("key", ref)
                .containsEntry("replayed", false)
                .containsEntry("replayedAt", null)
                .containsKeys("partition", "offset", "timestamp");
        assertThat((String) letter.get("exceptionClass")).endsWith("ShipmentNotFoundException");
        assertThat((String) letter.get("exceptionMessage")).contains(ref);
    }

    @Test
    void readingTheDeadLetterTopicCommitsNoOffsetOfTheServiceGroup() throws Exception {
        mvc.perform(get("/api/admin/dlt/scan-events").header("Authorization", TestAccounts.adminBearer(mvc)))
                .andExpect(status().isOk());

        try (AdminClient admin = admin()) {
            Map<TopicPartition, OffsetAndMetadata> offsets = admin.listConsumerGroupOffsets("shipment-service")
                    .partitionsToOffsetAndMetadata().get();
            assertThat(offsets.keySet()).noneMatch(partition -> partition.topic().equals("scan-events.DLT"));
        }
    }

    @Test
    void replayingAllAppliesAScanReceivedBeforeItsShipmentThenHasNothingLeftToReplay() throws Exception {
        String ref = "DLT-" + UUID.randomUUID();
        String payload = scan(ref, "PICKED_UP");
        kafka.send("scan-events", ref, payload);
        awaitDeadLetter(payload);
        shipments.create(ref, "Nantes", "Lyon", Instant.now().plus(Duration.ofDays(2)));

        Map<String, Object> job = replayAll();

        assertThat((Integer) job.get("replayed")).isPositive();
        assertThat(job).containsEntry("failed", 0).containsEntry("error", null)
                .containsEntry("requestedBy", "test-admin");
        assertThat(job.get("finishedAt")).isNotNull();
        awaitStatus(ref, ShipmentStatus.IN_TRANSIT);
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            Map<String, Object> progress = replayJob((String) job.get("id"));
            assertThat(progress.get("published")).isEqualTo(progress.get("replayed"));
        });
        assertThat(replayAll()).containsEntry("replayed", 0);
        Map<String, Object> letter = awaitDeadLetter(payload);
        assertThat(letter).containsEntry("replayed", true);
        assertThat(letter.get("replayedAt")).isNotNull();
    }

    @Test
    void replaysOneMessageWithoutTheDeadLetterHeadersThenRefusesToReplayItAgain() throws Exception {
        String ref = "DLT-" + UUID.randomUUID();
        String payload = scan(ref, "PICKED_UP");
        kafka.send("scan-events", ref, payload);
        Map<String, Object> letter = awaitDeadLetter(payload);
        shipments.create(ref, "Nantes", "Lyon", Instant.now().plus(Duration.ofDays(2)));
        String replay = "/api/admin/dlt/scan-events/%s/%s/replay".formatted(letter.get("partition"), letter.get("offset"));

        mvc.perform(post(replay).header("Authorization", TestAccounts.adminBearer(mvc)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value").value(payload))
                .andExpect(jsonPath("$.replayed").value(true))
                .andExpect(jsonPath("$.replayedAt").isNotEmpty());

        awaitStatus(ref, ShipmentStatus.IN_TRANSIT);
        List<ConsumerRecord<String, String>> published = scanEventsWithValue(payload);
        assertThat(published).hasSize(2); // le scan d'origine, puis son rejeu
        assertThat(published.getLast().key()).isEqualTo(ref);
        assertThat(published.getLast().headers().toArray()).extracting(Header::key)
                .noneMatch(name -> name.startsWith("kafka_dlt"));
        mvc.perform(post(replay).header("Authorization", TestAccounts.adminBearer(mvc)))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void replayingAMessageThatIsNotInTheDeadLetterTopicGets404() throws Exception {
        String admin = TestAccounts.adminBearer(mvc);

        for (String position : new String[] {"0/1000000", "7/0", "0/-1"}) {
            mvc.perform(post("/api/admin/dlt/scan-events/" + position + "/replay").header("Authorization", admin))
                    .andExpect(status().isNotFound())
                    .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
        }
    }

    @Test
    void replayingAllAnswers202BeforeTheReplayRuns() throws Exception {
        mvc.perform(post("/api/admin/dlt/scan-events/replay").header("Authorization", TestAccounts.adminBearer(mvc)))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Location",
                        matchesPattern(".*/api/admin/dlt/scan-events/replay/[0-9a-f-]{36}")))
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.finished").value(false))
                .andExpect(jsonPath("$.replayed").value(0));
    }

    @Test
    void anUnknownReplayGets404() throws Exception {
        mvc.perform(get("/api/admin/dlt/scan-events/replay/" + UUID.randomUUID())
                        .header("Authorization", TestAccounts.adminBearer(mvc)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void limitIsBetween1And500() throws Exception {
        String admin = TestAccounts.adminBearer(mvc);
        for (int i = 0; i < 2; i++) {
            String ref = "DLT-" + UUID.randomUUID();
            String payload = scan(ref, "PICKED_UP");
            kafka.send("scan-events", ref, payload);
            awaitDeadLetter(payload);
            shipments.create(ref, "Nantes", "Lyon", Instant.now().plus(Duration.ofDays(2)));
        }

        mvc.perform(get("/api/admin/dlt/scan-events?limit=1").header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/admin/dlt/scan-events?limit=500").header("Authorization", admin))
                .andExpect(status().isOk());
        for (String limit : new String[] {"501", "0"}) {
            mvc.perform(get("/api/admin/dlt/scan-events?limit=" + limit).header("Authorization", admin))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
        }
    }

    @Test
    void onlyAnAdminCanReadOrReplayTheDeadLetterTopic() throws Exception {
        String[] bearers = {TestAccounts.operatorBearer(mvc, accounts), TestAccounts.scannerBearer(mvc, accounts)};
        for (MockHttpServletRequestBuilder request : adminRoutes()) {
            mvc.perform(request)
                    .andExpect(status().isUnauthorized())
                    .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
        }
        for (String bearer : bearers) {
            for (MockHttpServletRequestBuilder request : adminRoutes()) {
                mvc.perform(request.header("Authorization", bearer))
                        .andExpect(status().isForbidden())
                        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
            }
        }
    }

    private static List<MockHttpServletRequestBuilder> adminRoutes() {
        return List.of(get("/api/admin/dlt/scan-events"),
                post("/api/admin/dlt/scan-events/0/0/replay"),
                post("/api/admin/dlt/scan-events/replay"),
                get("/api/admin/dlt/scan-events/replay/" + UUID.randomUUID()));
    }

    private List<ConsumerRecord<String, String>> scanEventsWithValue(String value) {
        Map<String, Object> config = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaContainer.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "dlt-api-test-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        List<ConsumerRecord<String, String>> found = new ArrayList<>();
        try (KafkaConsumer<String, String> consumer =
                     new KafkaConsumer<>(config, new StringDeserializer(), new StringDeserializer())) {
            consumer.subscribe(List.of("scan-events"));
            long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
            while (found.size() < 2 && System.nanoTime() < deadline) {
                consumer.poll(Duration.ofMillis(500)).forEach(record -> {
                    if (value.equals(record.value())) {
                        found.add(record);
                    }
                });
            }
        }
        return found;
    }

    /** Lance « tout rejouer » et attend la fin du parcours du DLT ; rend l'état final du rejeu. */
    private Map<String, Object> replayAll() throws Exception {
        String body = mvc.perform(post("/api/admin/dlt/scan-events/replay")
                        .header("Authorization", TestAccounts.adminBearer(mvc)))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(body, "$.id");
        return await().atMost(Duration.ofSeconds(30))
                .until(() -> replayJob(id), job -> Boolean.TRUE.equals(job.get("finished")));
    }

    private Map<String, Object> replayJob(String id) throws Exception {
        String body = mvc.perform(get("/api/admin/dlt/scan-events/replay/" + id)
                        .header("Authorization", TestAccounts.adminBearer(mvc)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$");
    }

    private void awaitStatus(String ref, ShipmentStatus expected) {
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(repository.findByReference(ref)).get()
                        .extracting(s -> s.status()).isEqualTo(expected));
    }

    private Map<String, Object> awaitDeadLetter(String payload) {
        return await().atMost(Duration.ofSeconds(30)).until(() -> deadLetters().stream()
                .filter(letter -> payload.equals(letter.get("value"))).findFirst().orElse(null),
                letter -> letter != null);
    }

    private List<Map<String, Object>> deadLetters() throws Exception {
        String body = mvc.perform(get("/api/admin/dlt/scan-events").header("Authorization", TestAccounts.adminBearer(mvc)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$");
    }

    private static String scan(String ref, String type) {
        return """
                {"eventId":"%s","shipmentReference":"%s","type":"%s","site":"Nantes","occurredAt":"2026-10-31T10:00:00Z"}"""
                .formatted(UUID.randomUUID(), ref, type);
    }

    private AdminClient admin() {
        return AdminClient.create(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaContainer.getBootstrapServers()));
    }
}
