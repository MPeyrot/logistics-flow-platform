package fr.michael.logistics.ai.infrastructure.claude;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import fr.michael.logistics.ai.application.ExtractionFailedException;
import fr.michael.logistics.ai.application.ExtractionUnavailableException;
import fr.michael.logistics.ai.application.ModelExtraction;
import fr.michael.logistics.ai.application.UnreadableDocumentException;
import fr.michael.logistics.ai.domain.DocumentFormat;
import fr.michael.logistics.ai.domain.SourceDocument;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** L'API Claude est simulée par WireMock : aucun appel réseau réel. */
class ClaudeDeliveryNoteExtractorTest {

    private static WireMockServer wireMock;

    private final SourceDocument pdf = new SourceDocument("%PDF-1.4 fake".getBytes(), DocumentFormat.PDF);
    private final SourceDocument png = new SourceDocument(
            new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1}, DocumentFormat.PNG);

    @BeforeAll
    static void startWireMock() {
        wireMock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMock.start();
    }

    @AfterAll
    static void stopWireMock() {
        wireMock.stop();
    }

    @BeforeEach
    void resetStubs() {
        wireMock.resetAll();
    }

    @Test
    void extractsNoteAndReportsUsage() {
        stubMessage(200, """
                {"reference":"BL-2026-0042","deliveryDate":"2026-10-30","senderName":"Acme","recipientName":" Dupont SA ",
                 "recipientAddress":"12 rue des Lilas, 69003 Lyon","packageCount":3,"totalWeightKg":42.5}""",
                1200, 90, 500, 2000);

        ModelExtraction result = extractor(true).extract(pdf);

        assertThat(result.note().reference()).isEqualTo("BL-2026-0042");
        assertThat(result.note().deliveryDate()).isEqualTo(LocalDate.of(2026, 10, 30));
        assertThat(result.note().recipientName()).isEqualTo("Dupont SA");
        assertThat(result.note().packageCount()).isEqualTo(3);
        assertThat(result.note().totalWeightKg()).isEqualByComparingTo(new BigDecimal("42.5"));
        assertThat(result.usage().inputTokens()).isEqualTo(1200);
        assertThat(result.usage().outputTokens()).isEqualTo(90);
        assertThat(result.usage().cacheWriteTokens()).isEqualTo(500);
        assertThat(result.usage().cacheReadTokens()).isEqualTo(2000);
    }

    @Test
    void sendsCacheableSystemPromptAndPdfDocument() {
        stubMessage(200, "{\"reference\":\"BL-1\"}", 10, 5, 0, 0);

        extractor(true).extract(pdf);

        wireMock.verify(postRequestedFor(urlEqualTo("/v1/messages"))
                .withHeader("Authorization", equalTo("Bearer test-key"))
                .withRequestBody(matchingJsonPath("$.model", equalTo("claude-opus-5-5")))
                .withRequestBody(matchingJsonPath("$.output_config.effort", equalTo("low")))
                .withRequestBody(matchingJsonPath("$.system[0].cache_control.type", equalTo("ephemeral")))
                .withRequestBody(matchingJsonPath("$.messages[0].content[0].type", equalTo("document")))
                .withRequestBody(matchingJsonPath("$.messages[0].content[0].source.media_type", equalTo("application/pdf")))
                .withRequestBody(matchingJsonPath("$.messages[0].content[1].type", equalTo("text")))
                .withRequestBody(com.github.tomakehurst.wiremock.client.WireMock.notMatching(".*tool_choice.*")));
    }

    @Test
    void sendsImagesAsImageBlocks() {
        stubMessage(200, "{\"reference\":\"BL-1\"}", 10, 5, 0, 0);

        extractor(true).extract(png);

        wireMock.verify(postRequestedFor(urlEqualTo("/v1/messages"))
                .withRequestBody(matchingJsonPath("$.messages[0].content[0].type", equalTo("image")))
                .withRequestBody(matchingJsonPath("$.messages[0].content[0].source.media_type", equalTo("image/png"))));
    }

    @Test
    void acceptsJsonWrappedInCodeFence() {
        stubMessage(200, "```json\\n{\\\"reference\\\":\\\"BL-9\\\"}\\n```", 10, 5, 0, 0, true);

        assertThat(extractor(true).extract(pdf).note().reference()).isEqualTo("BL-9");
    }

    @Test
    void rejectsMissingReferenceButKeepsUsage() {
        stubMessage(200, "{\"reference\":null}", 800, 12, 0, 0);

        assertThatThrownBy(() -> extractor(true).extract(pdf))
                .isInstanceOfSatisfying(UnreadableDocumentException.class, e -> {
                    assertThat(e.getMessage()).contains("reference");
                    assertThat(e.usage().inputTokens()).isEqualTo(800);
                });
    }

    @Test
    void rejectsValuesOutsideBeanValidationLimits() {
        stubMessage(200, "{\"reference\":\"BL-1\",\"packageCount\":0,\"totalWeightKg\":-3}", 10, 5, 0, 0);

        assertThatThrownBy(() -> extractor(true).extract(pdf))
                .isInstanceOf(UnreadableDocumentException.class)
                .hasMessageContaining("packageCount")
                .hasMessageContaining("totalWeightKg");
    }

    @Test
    void rejectsNonJsonAnswer() {
        stubMessage(200, "Je ne vois pas de bon de livraison.", 10, 5, 0, 0);

        assertThatThrownBy(() -> extractor(true).extract(pdf)).isInstanceOf(UnreadableDocumentException.class);
    }

    @Test
    void rejectsInvalidDate() {
        stubMessage(200, "{\"reference\":\"BL-1\",\"deliveryDate\":\"demain\"}", 10, 5, 0, 0);

        assertThatThrownBy(() -> extractor(true).extract(pdf)).isInstanceOf(UnreadableDocumentException.class);
    }

    /** Une erreur imprévue après la réponse du modèle (ici : le validateur plante) reste un appel facturé :
     *  UnreadableDocumentException avec l'usage, pas une 500 qui échappe au budget et aux métriques. */
    @Test
    void unexpectedFailureAfterTheModelAnsweredKeepsUsage() {
        stubMessage(200, "{\"reference\":\"BL-1\"}", 300, 7, 0, 0);
        Validator broken = org.mockito.Mockito.mock(Validator.class);
        org.mockito.Mockito.when(broken.validate(org.mockito.ArgumentMatchers.<DeliveryNoteDraft>any()))
                .thenThrow(new IllegalStateException("validator down"));

        assertThatThrownBy(() -> extractor(true, broken).extract(pdf))
                .isInstanceOfSatisfying(UnreadableDocumentException.class, e -> {
                    assertThat(e.usage().inputTokens()).isEqualTo(300);
                    assertThat(e.usage().outputTokens()).isEqualTo(7);
                    assertThat(e).hasCauseInstanceOf(IllegalStateException.class);
                });
    }

    @Test
    void wrapsProviderErrors() {
        wireMock.stubFor(post("/v1/messages").willReturn(aResponse().withStatus(500)
                .withHeader("content-type", "application/json")
                .withBody("{\"type\":\"error\",\"error\":{\"type\":\"api_error\",\"message\":\"boom\"}}")));

        assertThatThrownBy(() -> extractor(true).extract(pdf)).isInstanceOf(ExtractionFailedException.class);
    }

    @Test
    void refusesToCallWithoutApiKey() {
        assertThatThrownBy(() -> extractor(false).extract(pdf)).isInstanceOf(ExtractionUnavailableException.class);
        assertThat(wireMock.getAllServeEvents()).isEmpty();
    }

    private ClaudeDeliveryNoteExtractor extractor(boolean withKey) {
        return extractor(withKey, Validation.buildDefaultValidatorFactory().getValidator());
    }

    private ClaudeDeliveryNoteExtractor extractor(boolean withKey, Validator validator) {
        var props = new ClaudeProperties(withKey ? "test-key" : "", wireMock.baseUrl(), "claude-opus-5-5", "low",
                2048, 0, Duration.ofSeconds(10),
                Map.of("claude-opus-5-5",
                        new ClaudeProperties.Pricing(BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE,
                                null)));
        var client = AnthropicOkHttpClient.builder()
                .apiKey(withKey ? "test-key" : "unused")
                .baseUrl(wireMock.baseUrl())
                .maxRetries(0)
                .build();
        return new ClaudeDeliveryNoteExtractor(client, props, validator, JsonMapper.builder().build());
    }

    private void stubMessage(int status, String text, long in, long out, long cacheWrite, long cacheRead) {
        stubMessage(status, text, in, out, cacheWrite, cacheRead, false);
    }

    /** {@code escaped} : le texte est déjà échappé pour être inséré tel quel dans une chaîne JSON. */
    private void stubMessage(int status, String text, long in, long out, long cacheWrite, long cacheRead,
            boolean escaped) {
        String jsonText = escaped ? text : text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
        wireMock.stubFor(post("/v1/messages").willReturn(aResponse().withStatus(status)
                .withHeader("content-type", "application/json")
                .withBody("""
                        {"id":"msg_test","type":"message","role":"assistant","model":"claude-opus-5-5",
                         "content":[{"type":"text","text":"%s"}],
                         "stop_reason":"end_turn","stop_sequence":null,
                         "usage":{"input_tokens":%d,"output_tokens":%d,
                                  "cache_creation_input_tokens":%d,"cache_read_input_tokens":%d}}"""
                        .formatted(jsonText, in, out, cacheWrite, cacheRead))));
    }
}
