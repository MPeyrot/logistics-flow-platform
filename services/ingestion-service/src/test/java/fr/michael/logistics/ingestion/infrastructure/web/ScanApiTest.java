package fr.michael.logistics.ingestion.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import fr.michael.logistics.ingestion.TestcontainersConfiguration;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
