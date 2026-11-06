package fr.michael.logistics.ingestion.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import fr.michael.logistics.ingestion.TestcontainersConfiguration;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.testcontainers.kafka.KafkaContainer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import fr.michael.logistics.ingestion.infrastructure.security.TestJwt;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ScanApiTest {

    @DynamicPropertySource
    static void jwks(DynamicPropertyRegistry registry) {
        TestJwt.register(registry);
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    KafkaContainer kafka;

    @Autowired
    JsonMapper json;

    @Test
    void publishesScanOnKafkaKeyedByShipmentReference() throws Exception {
        String reference = "KAFKA-" + UUID.randomUUID();
        mvc.perform(post("/api/scans")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwt.scanner("Nantes").build())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"shipmentReference":"%s","type":"PICKED_UP","site":"Nantes"}""".formatted(reference)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.eventId").exists());

        ConsumerRecord<String, String> record = readRecordFor(reference);

        assertThat(record.key()).isEqualTo(reference);
        JsonNode message = json.readTree(record.value());
        assertThat(message.get("shipmentReference").asString()).isEqualTo(reference);
        assertThat(message.get("type").asString()).isEqualTo("PICKED_UP");
        assertThat(message.get("site").asString()).isEqualTo("Nantes");
        assertThat(message.get("eventId").asString()).isNotBlank();
        assertThat(message.get("occurredAt").asString()).isNotBlank();
    }

    @Test
    void publishesFailedDeliveryWithItsReasonInTheV2Shape() throws Exception {
        String reference = "KAFKA-" + UUID.randomUUID();
        mvc.perform(post("/api/scans")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwt.scanner("Nantes").build())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"shipmentReference":"%s","type":"DELIVERY_FAILED","site":"Nantes","reason":"Destinataire absent"}"""
                                .formatted(reference)))
                .andExpect(status().isAccepted());

        JsonNode message = json.readTree(readRecordFor(reference).value());

        assertThat(message.get("type").asString()).isEqualTo("DELIVERY_FAILED");
        assertThat(message.get("reason").asString()).isEqualTo("Destinataire absent");
        assertConformsToV2Contract(message);
    }

    @Test
    void publishesScanWithoutReasonInTheV2Shape() throws Exception {
        String reference = "KAFKA-" + UUID.randomUUID();
        mvc.perform(post("/api/scans")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwt.scanner("Nantes").build())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"shipmentReference":"%s","type":"OUT_FOR_DELIVERY","site":"Nantes"}""".formatted(reference)))
                .andExpect(status().isAccepted());

        JsonNode message = json.readTree(readRecordFor(reference).value());

        assertThat(message.has("reason")).as("reason absent, not null").isFalse();
        assertConformsToV2Contract(message);
    }

    static List<String> invalidReasons() {
        return List.of(
                "\"type\":\"PICKED_UP\",\"reason\":\"Destinataire absent\"",
                "\"type\":\"DELIVERY_FAILED\",\"reason\":\"\"",
                "\"type\":\"DELIVERY_FAILED\",\"reason\":\"   \"",
                "\"type\":\"DELIVERY_FAILED\",\"reason\":\"\u00a0 \u3000\"",
                "\"type\":\"DELIVERY_FAILED\",\"reason\":\"" + "\uD83D\uDE00".repeat(201) + "\"",
                "\"type\":\"DELIVERY_FAILED\",\"reason\":\"" + "x".repeat(201) + "\"");
    }

    @ParameterizedTest
    @MethodSource("invalidReasons")
    void rejectsMisplacedEmptyOrTooLongReasonWithAProblemDetail(String typeAndReason) throws Exception {
        String reference = "KAFKA-" + UUID.randomUUID();
        mvc.perform(post("/api/scans")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwt.scanner("Nantes").build())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"shipmentReference":"%s",%s,"site":"Nantes"}""".formatted(reference, typeAndReason)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("reason")));
    }

    @Test
    void countsTheReasonLengthInCodePointsLikeTheContract() throws Exception {
        mvc.perform(post("/api/scans")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwt.scanner("Nantes").build())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"shipmentReference":"KAFKA-%s","type":"DELIVERY_FAILED","site":"Nantes","reason":"%s"}"""
                                .formatted(UUID.randomUUID(), "\uD83D\uDE00".repeat(200))))
                .andExpect(status().isAccepted());
    }

    @Test
    void reportsAnEmptyReasonOnce() throws Exception {
        mvc.perform(post("/api/scans")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwt.scanner("Nantes").build())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"shipmentReference":"REF","type":"DELIVERY_FAILED","site":"Nantes","reason":""}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("reason: must not be blank"));
    }

    @Test
    void rejectsOccurredAtGivenAsANumber() throws Exception {
        mvc.perform(post("/api/scans")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwt.scanner("Nantes").build())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"shipmentReference":"REF","type":"PICKED_UP","site":"Nantes","occurredAt":1767225600}"""))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Invalid value for occurredAt"));
    }

    @Test
    void answersAProblemDetailOnMalformedJsonOrUnknownType() throws Exception {
        mvc.perform(post("/api/scans")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwt.scanner("Nantes").build())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"shipmentReference\":"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Malformed request body"));
        mvc.perform(post("/api/scans")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwt.scanner("Nantes").build())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"shipmentReference":"REF","type":"UNKNOWN","site":"Nantes"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Invalid value for type"));
    }

    @Test
    void acceptsReasonOf200Characters() throws Exception {
        mvc.perform(post("/api/scans")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwt.scanner("Nantes").build())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"shipmentReference":"KAFKA-%s","type":"DELIVERY_FAILED","site":"Nantes","reason":"%s"}"""
                                .formatted(UUID.randomUUID(), "x".repeat(200))))
                .andExpect(status().isAccepted());
    }

    /**
     * Le contrat fait foi : champs obligatoires présents, aucun champ hors du schéma, type parmi ceux de v2.
     * Les règles de {@code reason} sont vérifiées par les tests de refus ci-dessus.
     */
    private void assertConformsToV2Contract(JsonNode message) throws Exception {
        JsonNode schema = json.readTree(Files.readString(Path.of("../../contracts/scan-event.v2.schema.json")));
        Set<String> properties = new HashSet<>(schema.get("properties").propertyNames());
        List<String> required = schema.get("required").valueStream().map(JsonNode::asString).toList();
        List<String> types = schema.get("properties").get("type").get("enum").valueStream().map(JsonNode::asString).toList();

        assertThat(message.propertyNames()).containsAll(required);
        assertThat(properties).containsAll(message.propertyNames());
        assertThat(types).contains(message.get("type").asString());
    }

    @Test
    void rejectsInvalidScanWithoutPublishing() throws Exception {
        mvc.perform(post("/api/scans")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwt.scanner("Nantes").build())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"shipmentReference":"","type":"PICKED_UP","site":"Nantes"}"""))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/scans")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwt.scanner("Nantes").build())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"shipmentReference":"REF","type":"UNKNOWN","site":"Nantes"}"""))
                .andExpect(status().isBadRequest());
    }

    private ConsumerRecord<String, String> readRecordFor(String reference) {
        Map<String, Object> config = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "test-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(config)) {
            consumer.subscribe(List.of("scan-events"));
            long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
            while (System.nanoTime() < deadline) {
                for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(500))) {
                    if (reference.equals(record.key())) {
                        return record;
                    }
                }
            }
        }
        throw new AssertionError("No record with key " + reference + " on scan-events");
    }
}
