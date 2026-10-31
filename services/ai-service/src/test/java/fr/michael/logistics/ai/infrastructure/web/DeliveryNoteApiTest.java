package fr.michael.logistics.ai.infrastructure.web;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** Parcours complet (HTTP, cas d'usage, adaptateur Claude, coût) contre une fausse API Claude. */
@SpringBootTest
@AutoConfigureMockMvc
class DeliveryNoteApiTest {

    private static final WireMockServer WIRE_MOCK = new WireMockServer(WireMockConfiguration.options().dynamicPort());

    static {
        WIRE_MOCK.start();
    }

    @DynamicPropertySource
    static void claude(DynamicPropertyRegistry registry) {
        registry.add("ai.claude.base-url", WIRE_MOCK::baseUrl);
        registry.add("ai.claude.api-key", () -> "test-key");
        registry.add("ai.claude.max-retries", () -> "0");
    }

    @AfterAll
    static void stop() {
        WIRE_MOCK.stop();
    }

    @Autowired
    MockMvc mvc;

    @BeforeEach
    void reset() {
        WIRE_MOCK.resetAll();
    }

    @Test
    void extractsDeliveryNoteAndReportsCost() throws Exception {
        stub(200, "{\\\"reference\\\":\\\"BL-77\\\",\\\"deliveryDate\\\":\\\"2026-10-30\\\",\\\"packageCount\\\":2}",
                1000, 100, 500, 2000);

        mvc.perform(multipart("/api/delivery-notes/extract").file(file("%PDF-1.4 fake".getBytes())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deliveryNote.reference").value("BL-77"))
                .andExpect(jsonPath("$.deliveryNote.deliveryDate").value("2026-10-30"))
                .andExpect(jsonPath("$.deliveryNote.packageCount").value(2))
                .andExpect(jsonPath("$.usage.cacheReadTokens").value(2000))
                // 1000*4 + 100*20 + 500*5 + 2000*0,20 = 8900 micro-dollars
                .andExpect(jsonPath("$.costUsd").value(0.0089));
    }

    @Test
    void rejectsFilesThatAreNotPdfOrImage() throws Exception {
        mvc.perform(multipart("/api/delivery-notes/extract").file(file("hello".getBytes())))
                .andExpect(status().isUnsupportedMediaType());
        org.assertj.core.api.Assertions.assertThat(WIRE_MOCK.getAllServeEvents()).isEmpty();
    }

    @Test
    void answers422WhenNoReferenceCanBeRead() throws Exception {
        stub(200, "{\\\"reference\\\":null}", 800, 10, 0, 0);

        mvc.perform(multipart("/api/delivery-notes/extract").file(file("%PDF-1.4 fake".getBytes())))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void answers502WhenClaudeFails() throws Exception {
        WIRE_MOCK.stubFor(post("/v1/messages").willReturn(aResponse().withStatus(529)
                .withHeader("content-type", "application/json")
                .withBody("{\"type\":\"error\",\"error\":{\"type\":\"overloaded_error\",\"message\":\"Overloaded\"}}")));

        mvc.perform(multipart("/api/delivery-notes/extract").file(file("%PDF-1.4 fake".getBytes())))
                .andExpect(status().isBadGateway());
    }

    private static MockMultipartFile file(byte[] content) {
        return new MockMultipartFile("file", "bl.pdf", "application/pdf", content);
    }

    private static void stub(int status, String escapedText, long in, long out, long cacheWrite, long cacheRead) {
        WIRE_MOCK.stubFor(post("/v1/messages").willReturn(aResponse().withStatus(status)
                .withHeader("content-type", "application/json")
                .withBody("""
                        {"id":"msg_test","type":"message","role":"assistant","model":"claude-opus-5-5",
                         "content":[{"type":"text","text":"%s"}],"stop_reason":"end_turn","stop_sequence":null,
                         "usage":{"input_tokens":%d,"output_tokens":%d,
                                  "cache_creation_input_tokens":%d,"cache_read_input_tokens":%d}}"""
                        .formatted(escapedText, in, out, cacheWrite, cacheRead))));
    }
}
