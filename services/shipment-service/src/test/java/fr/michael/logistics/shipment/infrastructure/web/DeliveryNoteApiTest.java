package fr.michael.logistics.shipment.infrastructure.web;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import fr.michael.logistics.shipment.TestAccounts;
import fr.michael.logistics.shipment.TestcontainersConfiguration;
import fr.michael.logistics.shipment.application.AccountService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** Parcours complet contre une base réelle et un ai-service simulé par WireMock. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class DeliveryNoteApiTest {

    private static final WireMockServer AI_SERVICE = new WireMockServer(WireMockConfiguration.options().dynamicPort());

    static {
        AI_SERVICE.start();
    }

    @DynamicPropertySource
    static void aiService(DynamicPropertyRegistry registry) {
        registry.add("ai-service.base-url", AI_SERVICE::baseUrl);
    }

    @AfterAll
    static void stop() {
        AI_SERVICE.stop();
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    AccountService accounts;

    String bearer;

    @BeforeEach
    void reset() throws Exception {
        AI_SERVICE.resetAll();
        bearer = TestAccounts.operatorBearer(mvc, accounts);
    }

    @Test
    void returnsDeliveryNoteWithMatchingShipment() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/shipments").header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reference\":\"BL-MATCH\",\"origin\":\"Nantes\",\"destination\":\"Lyon\"}"))
                .andExpect(status().isCreated());
        stub(200, body("BL-MATCH"));

        mvc.perform(multipart("/api/delivery-notes/extract").file(pdf()).header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deliveryNote.reference").value("BL-MATCH"))
                .andExpect(jsonPath("$.deliveryNote.packageCount").value(2))
                .andExpect(jsonPath("$.costUsd").value(0.0089))
                .andExpect(jsonPath("$.shipment.status").value("CREATED"))
                .andExpect(jsonPath("$.shipment.origin").value("Nantes"));
    }

    @Test
    void returnsNullShipmentWhenReferenceIsUnknown() throws Exception {
        stub(200, body("BL-UNKNOWN"));

        mvc.perform(multipart("/api/delivery-notes/extract").file(pdf()).header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deliveryNote.reference").value("BL-UNKNOWN"))
                .andExpect(jsonPath("$.shipment").doesNotExist());
    }

    @Test
    void relaysUnreadableDocumentAs422() throws Exception {
        stub(422, "{}");
        mvc.perform(multipart("/api/delivery-notes/extract").file(pdf()).header("Authorization", bearer)).andExpect(status().isUnprocessableContent());
    }

    @Test
    void relaysUnsupportedFormatAs415() throws Exception {
        stub(415, "{}");
        mvc.perform(multipart("/api/delivery-notes/extract").file(pdf()).header("Authorization", bearer)).andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void answers502WhenAiServiceFails() throws Exception {
        stub(503, "{}");
        mvc.perform(multipart("/api/delivery-notes/extract").file(pdf()).header("Authorization", bearer)).andExpect(status().isBadGateway());
    }

    private static MockMultipartFile pdf() {
        return new MockMultipartFile("file", "bl.pdf", "application/pdf", "%PDF-1.4 fake".getBytes());
    }

    private static String body(String reference) {
        return """
                {"deliveryNote":{"reference":"%s","packageCount":2},
                 "usage":{"inputTokens":1000,"outputTokens":100,"cacheWriteTokens":500,"cacheReadTokens":2000},
                 "costUsd":0.008900}""".formatted(reference);
    }

    private static void stub(int status, String body) {
        AI_SERVICE.stubFor(post("/api/delivery-notes/extract").willReturn(aResponse().withStatus(status)
                .withHeader("content-type", "application/json").withBody(body)));
    }
}
