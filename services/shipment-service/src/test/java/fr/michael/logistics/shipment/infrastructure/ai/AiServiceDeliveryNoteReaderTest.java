package fr.michael.logistics.shipment.infrastructure.ai;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import fr.michael.logistics.shipment.application.DeliveryNoteReading;
import fr.michael.logistics.shipment.application.ExtractionServiceUnavailableException;
import fr.michael.logistics.shipment.application.ExtractionServiceUnreachableException;
import fr.michael.logistics.shipment.application.ExtractionRateLimitedException;
import fr.michael.logistics.shipment.application.ServiceTokenProvider;
import fr.michael.logistics.shipment.application.TokenUsage;
import fr.michael.logistics.shipment.application.UnreadableDeliveryNoteException;
import fr.michael.logistics.shipment.application.UnsupportedDocumentException;
import jakarta.validation.Validation;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** L'ai-service est simulé par WireMock. */
class AiServiceDeliveryNoteReaderTest {

    private static WireMockServer wireMock;

    @BeforeAll
    static void start() {
        wireMock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMock.start();
    }

    @AfterAll
    static void stop() {
        wireMock.stop();
    }

    @BeforeEach
    void reset() {
        wireMock.resetAll();
    }

    @Test
    void sendsFileAsMultipartAndMapsResponse() {
        stubJson(200, """
                {"deliveryNote":{"reference":"BL-42","deliveryDate":"2026-10-30","senderName":"Acme",
                 "recipientName":"Dupont","recipientAddress":"12 rue des Lilas, Lyon","packageCount":3,"totalWeightKg":42.5},
                 "usage":{"inputTokens":1,"outputTokens":2,"cacheWriteTokens":3,"cacheReadTokens":4},"costUsd":0.0089}""");

        DeliveryNoteReading reading = reader(wireMock.baseUrl()).read("%PDF-1.4 fake".getBytes(), "bl.pdf");

        assertThat(reading.note().reference()).isEqualTo("BL-42");
        assertThat(reading.note().deliveryDate()).isEqualTo(LocalDate.of(2026, 10, 30));
        assertThat(reading.note().packageCount()).isEqualTo(3);
        assertThat(reading.costUsd()).isEqualByComparingTo("0.0089");
        assertThat(reading.usage()).isEqualTo(new TokenUsage(1, 2, 3, 4));
        wireMock.verify(postRequestedFor(urlEqualTo("/api/delivery-notes/extract"))
                .withHeader("Authorization", com.github.tomakehurst.wiremock.client.WireMock.equalTo("Bearer signed-token"))
                .withHeader("Content-Type", containing("multipart/form-data"))
                .withRequestBody(containing("name=\"file\"; filename=\"bl.pdf\"")));
    }

    @Test
    void translatesAiServiceStatuses() {
        var reader = reader(wireMock.baseUrl());

        stubJson(415, "{}");
        assertThatThrownBy(() -> reader.read(new byte[] {1}, "x")).isInstanceOf(UnsupportedDocumentException.class);

        stubJson(422, "{}");
        assertThatThrownBy(() -> reader.read(new byte[] {1}, "x")).isInstanceOf(UnreadableDeliveryNoteException.class);

        stubJson(400, "{}");
        assertThatThrownBy(() -> reader.read(new byte[] {1}, "x")).isInstanceOf(IllegalArgumentException.class);

        stubJson(502, "{}");
        assertThatThrownBy(() -> reader.read(new byte[] {1}, "x")).isInstanceOf(ExtractionServiceUnavailableException.class);

        stubJson(401, "{}");
        assertThatThrownBy(() -> reader.read(new byte[] {1}, "x")).isInstanceOf(ExtractionServiceUnavailableException.class);

        stubJson(403, "{}");
        assertThatThrownBy(() -> reader.read(new byte[] {1}, "x")).isInstanceOf(ExtractionServiceUnavailableException.class);
    }

    @Test
    void relaysRateLimitWithRetryAfter() {
        wireMock.stubFor(post("/api/delivery-notes/extract").willReturn(aResponse().withStatus(429)
                .withHeader("Retry-After", "120").withBody("{}")));

        assertThatThrownBy(() -> reader(wireMock.baseUrl()).read(new byte[] {1}, "x"))
                .isInstanceOfSatisfying(ExtractionRateLimitedException.class,
                        e -> assertThat(e.retryAfterSeconds()).isEqualTo(120L));
    }

    @Test
    void rejectsResponseWithoutReference() {
        stubJson(200, "{\"deliveryNote\":{\"reference\":\"\"}," + USAGE + ",\"costUsd\":0.01}");

        assertThatThrownBy(() -> reader(wireMock.baseUrl()).read(new byte[] {1}, "x"))
                .isInstanceOf(ExtractionServiceUnavailableException.class);
    }

    @Test
    void rejectsResponseWithoutUsage() {
        stubJson(200, "{\"deliveryNote\":{\"reference\":\"BL-1\"},\"costUsd\":0.01}");

        assertThatThrownBy(() -> reader(wireMock.baseUrl()).read(new byte[] {1}, "x"))
                .isInstanceOf(ExtractionServiceUnavailableException.class);
    }

    @Test
    void rejectsUsageWithAMissingOrNegativeTokenCount() {
        stubJson(200, """
                {"deliveryNote":{"reference":"BL-1"},"costUsd":0.01,
                 "usage":{"inputTokens":1,"outputTokens":1,"cacheWriteTokens":0}}""");
        assertThatThrownBy(() -> reader(wireMock.baseUrl()).read(new byte[] {1}, "x"))
                .isInstanceOf(ExtractionServiceUnavailableException.class);

        stubJson(200, """
                {"deliveryNote":{"reference":"BL-1"},"costUsd":0.01,
                 "usage":{"inputTokens":-1,"outputTokens":1,"cacheWriteTokens":0,"cacheReadTokens":0}}""");
        assertThatThrownBy(() -> reader(wireMock.baseUrl()).read(new byte[] {1}, "x"))
                .isInstanceOf(ExtractionServiceUnavailableException.class);
    }

    private static final String USAGE =
            "\"usage\":{\"inputTokens\":1,\"outputTokens\":1,\"cacheWriteTokens\":0,\"cacheReadTokens\":0}";

    @Test
    void reportsUnreachableService() {
        // port fermé : rien n'écoute sur le port 1
        assertThatThrownBy(() -> reader("http://localhost:1").read(new byte[] {1}, "x"))
                .isInstanceOf(ExtractionServiceUnreachableException.class);
    }

    @Test
    void reportsUnknownHostAsUnreachable() {
        // .invalid : domaine réservé, jamais résolu
        assertThatThrownBy(() -> reader("http://ai-service.invalid").read(new byte[] {1}, "x"))
                .isInstanceOf(ExtractionServiceUnreachableException.class);
    }

    /** La requête est partie : ai-service a pu appeler Claude, ce n'est pas « injoignable ». */
    @Test
    void readTimeoutIsNotReportedAsUnreachable() {
        wireMock.stubFor(post("/api/delivery-notes/extract").willReturn(aResponse().withStatus(200)
                .withFixedDelay(2_000).withHeader("content-type", "application/json").withBody("{}")));

        assertThatThrownBy(() -> reader(wireMock.baseUrl(), Duration.ofMillis(300)).read(new byte[] {1}, "x"))
                .isInstanceOf(ExtractionServiceUnavailableException.class)
                .isNotInstanceOf(ExtractionServiceUnreachableException.class);
    }

    private static AiServiceDeliveryNoteReader reader(String baseUrl) {
        return reader(baseUrl, Duration.ofSeconds(5));
    }

    private static AiServiceDeliveryNoteReader reader(String baseUrl, Duration readTimeout) {
        var props = new AiServiceProperties(baseUrl, Duration.ofSeconds(1), readTimeout);
        return new AiServiceDeliveryNoteReader(props, Validation.buildDefaultValidatorFactory().getValidator(),
                new ServiceTokenProvider("ai-service", claims -> "signed-token", Clock.systemUTC()));
    }

    private static void stubJson(int status, String body) {
        wireMock.stubFor(post("/api/delivery-notes/extract").willReturn(aResponse().withStatus(status)
                .withHeader("content-type", "application/json").withBody(body)));
    }
}
