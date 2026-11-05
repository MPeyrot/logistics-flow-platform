package fr.michael.logistics.shipment.infrastructure.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import fr.michael.logistics.shipment.TestAccounts;
import fr.michael.logistics.shipment.TestcontainersConfiguration;
import fr.michael.logistics.shipment.application.AccountService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Matrice d'accès de la spec #13 côté shipment-service, avec de vrais jetons. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AccessMatrixTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    AccountService accounts;

    @Test
    void adminAndOperatorCanListAndCreateShipments() throws Exception {
        for (String bearer : new String[] {TestAccounts.adminBearer(mvc), TestAccounts.operatorBearer(mvc, accounts)}) {
            mvc.perform(get("/api/shipments").header("Authorization", bearer)).andExpect(status().isOk());
        }
        mvc.perform(createShipment("MATRIX-ADMIN").header("Authorization", TestAccounts.adminBearer(mvc)))
                .andExpect(status().isCreated());
        mvc.perform(createShipment("MATRIX-OPERATOR").header("Authorization", TestAccounts.operatorBearer(mvc, accounts)))
                .andExpect(status().isCreated());
    }

    @Test
    void missingTokenGets401Problem() throws Exception {
        unauthorized(mvc.perform(get("/api/shipments")));
        unauthorized(mvc.perform(createShipment("MATRIX-ANON")));
        unauthorized(mvc.perform(multipart("/api/delivery-notes/extract").file(pdf())));
    }

    @Test
    void invalidTokenGets401Problem() throws Exception {
        String token = TestAccounts.token(mvc, "test-admin", "test-admin-password");
        String tampered = token.substring(0, token.length() - 4) + "AAAA";

        unauthorized(mvc.perform(get("/api/shipments").header("Authorization", "Bearer " + tampered)));
        unauthorized(mvc.perform(get("/api/shipments").header("Authorization", "Bearer not-a-jwt")));
    }

    @Test
    void scannerGets403ProblemOnShipmentRoutes() throws Exception {
        String scanner = TestAccounts.scannerBearer(mvc, accounts);

        forbidden(mvc.perform(get("/api/shipments").header("Authorization", scanner)));
        forbidden(mvc.perform(createShipment("MATRIX-SCANNER").header("Authorization", scanner)));
        forbidden(mvc.perform(multipart("/api/delivery-notes/extract").file(pdf()).header("Authorization", scanner)));
    }

    @Test
    void healthIsPublicAndOtherActuatorEndpointsAreClosed() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());

        mvc.perform(get("/actuator/info")).andExpect(status().isUnauthorized());
        mvc.perform(get("/actuator/info").header("Authorization", TestAccounts.adminBearer(mvc)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/actuator/env").header("Authorization", TestAccounts.adminBearer(mvc)))
                .andExpect(status().isForbidden());
    }

    @Test
    void jwksIsPublic() throws Exception {
        mvc.perform(get("/.well-known/jwks.json")).andExpect(status().isOk());
    }

    private static void unauthorized(ResultActions result) throws Exception {
        result.andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.title").value("Unauthorized"));
    }

    private static void forbidden(ResultActions result) throws Exception {
        result.andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.title").value("Forbidden"));
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder createShipment(String reference) {
        return post("/api/shipments").contentType(MediaType.APPLICATION_JSON)
                .content("{\"reference\":\"%s\",\"origin\":\"Nantes\",\"destination\":\"Lyon\",\"expectedDeliveryAt\":\"%s\"}".formatted(reference, ShipmentApiTest.DUE));
    }

    private static MockMultipartFile pdf() {
        return new MockMultipartFile("file", "bl.pdf", "application/pdf", "%PDF-1.4 fake".getBytes());
    }
}
