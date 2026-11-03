package fr.michael.logistics.ai.infrastructure.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

/** Les refus ne doivent jamais atteindre le cas d'usage : sans clé Claude configurée, un passage donnerait 503. */
@SpringBootTest
@AutoConfigureMockMvc
class ServiceTokenSecurityTest {

    @DynamicPropertySource
    static void jwks(DynamicPropertyRegistry registry) {
        TestJwt.register(registry);
        registry.add("ai.claude.api-key", () -> "");
    }

    @Autowired
    MockMvc mvc;

    @Test
    void rejectsRequestWithoutToken() throws Exception {
        mvc.perform(extract(null))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void rejectsTokenWithWrongSignature() throws Exception {
        mvc.perform(extract(TestJwt.service().signedWithOtherKey().build()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsExpiredToken() throws Exception {
        mvc.perform(extract(TestJwt.service().expiresAt(Instant.now().minusSeconds(120)).build()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsTokenForAnotherAudience() throws Exception {
        mvc.perform(extract(TestJwt.service().audience("ingestion-service").build()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsUserToken() throws Exception {
        mvc.perform(extract(TestJwt.service().subject("alice").build()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void acceptsServiceTokenUpToTheUseCase() throws Exception {
        mvc.perform(extract(TestJwt.service().build()))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void healthStaysPublicAndOtherActuatorEndpointsAreClosed() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mvc.perform(get("/actuator/info")).andExpect(status().isUnauthorized());
        mvc.perform(get("/actuator/info").header(HttpHeaders.AUTHORIZATION,
                "Bearer " + TestJwt.service().build())).andExpect(status().isForbidden());
    }

    private static MockMultipartHttpServletRequestBuilder extract(String token) {
        MockMultipartHttpServletRequestBuilder request = multipart("/api/delivery-notes/extract")
                .file(new MockMultipartFile("file", "bl.pdf", "application/pdf", "%PDF-1.4 fake".getBytes()));
        if (token != null) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return request;
    }
}
