package fr.michael.logistics.ai.infrastructure;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import fr.michael.logistics.ai.infrastructure.security.TestJwt;
import java.util.Arrays;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Avec le profil {@code gcp}, une extraction facturée écrit une ligne JSON lisible par Cloud Logging. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("gcp")
@ExtendWith(OutputCaptureExtension.class)
class GcpLoggingTest {

    private static final WireMockServer WIRE_MOCK = new WireMockServer(WireMockConfiguration.options().dynamicPort());
    private static final ObjectMapper JSON = new ObjectMapper();

    static {
        WIRE_MOCK.start();
        // Les contextes des autres classes ont déjà configuré la journalisation de la JVM ; sans cela, Spring Boot
        // ne la reconfigure pas avec le profil gcp.
        LoggingSystem.get(GcpLoggingTest.class.getClassLoader()).cleanUp();
    }

    @DynamicPropertySource
    static void claude(DynamicPropertyRegistry registry) {
        registry.add("ai.claude.base-url", WIRE_MOCK::baseUrl);
        registry.add("ai.claude.api-key", () -> "test-key");
        registry.add("ai.claude.max-retries", () -> "0");
        TestJwt.register(registry);
    }

    @AfterAll
    static void stop() {
        WIRE_MOCK.stop();
        LoggingSystem.get(GcpLoggingTest.class.getClassLoader()).cleanUp();
    }

    @Autowired
    MockMvc mvc;

    @Test
    void lesNiveauxLogbackPrennentLesNomsDeCloudLogging(CapturedOutput output) {
        org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger("severity-test");
        logger.warn("avertissement-gcp");
        logger.error("erreur-gcp");

        assertThat(severityOf(output, "avertissement-gcp")).isEqualTo("WARNING");
        assertThat(severityOf(output, "erreur-gcp")).isEqualTo("ERROR");
    }

    private static String severityOf(CapturedOutput output, String message) {
        return Arrays.stream(output.getAll().split("\n"))
                .filter(l -> l.startsWith("{") && l.contains(message))
                .map(JSON::readTree)
                .findFirst().orElseThrow(() -> new AssertionError("aucune ligne JSON : " + output.getAll()))
                .get("severity").asString();
    }

    @Test
    void extractionFactureeEstUneLigneJsonAvecSeverityEtCostUsd(CapturedOutput output) throws Exception {
        WIRE_MOCK.stubFor(post("/v1/messages").willReturn(aResponse().withStatus(200)
                .withHeader("content-type", "application/json")
                .withBody("""
                        {"id":"msg_test","type":"message","role":"assistant","model":"claude-haiku-5-5",
                         "content":[{"type":"text","text":"{\\"reference\\":\\"BL-77\\",\\"deliveryDate\\":\\"2026-10-30\\",\\"packageCount\\":2}"}],
                         "stop_reason":"end_turn","stop_sequence":null,
                         "usage":{"input_tokens":1000,"output_tokens":100,
                                  "cache_creation_input_tokens":0,"cache_read_input_tokens":0}}""")));

        mvc.perform(multipart("/api/delivery-notes/extract").header("Authorization", "Bearer " + TestJwt.service().build())
                        .file(new MockMultipartFile("file", "bl.pdf", "application/pdf", "%PDF-1.4 fake".getBytes())))
                .andExpect(status().isOk());

        JsonNode line = Arrays.stream(output.getAll().split("\n"))
                .filter(l -> l.startsWith("{") && l.contains("delivery note extracted"))
                .map(JSON::readTree)
                .findFirst().orElseThrow(() -> new AssertionError("aucune ligne JSON d'extraction dans : " + output.getAll()));
        assertThat(line.get("severity").asString()).isEqualTo("INFO");
        assertThat(line.get("message").asString()).startsWith("delivery note extracted");
        assertThat(line.get("costUsd").isNumber()).isTrue();
        assertThat(line.get("costUsd").decimalValue()).isPositive();
        assertThat(line.get("model").asString()).isEqualTo("claude-haiku-5-5");
        assertThat(line.get("outcome").asString()).isEqualTo("extracted");
    }
}
