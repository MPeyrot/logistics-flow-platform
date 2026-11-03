package fr.michael.logistics.ingestion.infrastructure.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import fr.michael.logistics.ingestion.TestcontainersConfiguration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ScanSecurityTest {

    @DynamicPropertySource
    static void jwks(DynamicPropertyRegistry registry) {
        TestJwt.register(registry);
    }

    @Autowired
    MockMvc mvc;

    @Test
    void acceptsScanWithValidScannerToken() throws Exception {
        mvc.perform(scan("Nantes", TestJwt.scanner("Nantes").build()))
                .andExpect(status().isAccepted());
    }

    @Test
    void rejectsScanWithoutToken() throws Exception {
        mvc.perform(scan("Nantes", null))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void rejectsTokenWithWrongSignature() throws Exception {
        mvc.perform(scan("Nantes", TestJwt.scanner("Nantes").signedWithOtherKey().build()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsExpiredToken() throws Exception {
        mvc.perform(scan("Nantes", TestJwt.scanner("Nantes").expiresAt(Instant.now().minusSeconds(120)).build()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsTokenForAnotherAudience() throws Exception {
        mvc.perform(scan("Nantes", TestJwt.scanner("Nantes").audience("shipment-service").build()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsTokenWithoutScannerRole() throws Exception {
        mvc.perform(scan("Nantes", TestJwt.scanner("Nantes").roles("OPERATOR").build()))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void rejectsScanForAnotherSite() throws Exception {
        mvc.perform(scan("Lyon", TestJwt.scanner("Nantes").build()))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void healthIsPublicButOtherActuatorEndpointsAreClosed() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mvc.perform(get("/actuator/info")).andExpect(status().isUnauthorized());
    }

    private MockHttpServletRequestBuilder scan(String site, String token) {
        MockHttpServletRequestBuilder request = post("/api/scans")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"shipmentReference":"SEC-%s","type":"PICKED_UP","site":"%s"}""".formatted(UUID.randomUUID(), site));
        return token == null ? request : request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }
}
