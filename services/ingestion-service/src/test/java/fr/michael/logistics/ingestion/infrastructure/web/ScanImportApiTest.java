package fr.michael.logistics.ingestion.infrastructure.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import fr.michael.logistics.ingestion.TestcontainersConfiguration;
import fr.michael.logistics.ingestion.infrastructure.security.TestJwt;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

@SpringBootTest(properties = "ingestion.import.max-lines=3")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ScanImportApiTest {

    private static final String HEADER = "shipmentReference,type,site,occurredAt\n";

    @DynamicPropertySource
    static void jwks(DynamicPropertyRegistry registry) {
        TestJwt.register(registry);
    }

    @Autowired
    MockMvc mvc;

    @Test
    void importsAFileAndReportsRejectedLinesEvenWhenEveryLineIsRejected() throws Exception {
        mvc.perform(upload(HEADER + "A-1,PICKED_UP,Nantes,2026-11-07T08:00:00Z\nA-2,NOPE,Nantes,2026-11-07T08:00:00Z\n",
                        TestJwt.scanner("Nantes").build()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines").value(2))
                .andExpect(jsonPath("$.published").value(1))
                .andExpect(jsonPath("$.rejected").value(1))
                .andExpect(jsonPath("$.errors[0].line").value(3))
                .andExpect(jsonPath("$.errors[0].detail").isNotEmpty());

        mvc.perform(upload(HEADER + "A-3,PICKED_UP,Lyon,2026-11-07T08:00:00Z\n", TestJwt.scanner("Nantes").build()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.published").value(0))
                .andExpect(jsonPath("$.rejected").value(1));
    }

    @Test
    void requiresAScannerToken() throws Exception {
        String csv = HEADER + "A-1,PICKED_UP,Nantes,2026-11-07T08:00:00Z\n";
        mvc.perform(upload(csv, null)).andExpect(status().isUnauthorized());
        mvc.perform(upload(csv, TestJwt.scanner("Nantes").roles("OPERATOR").build())).andExpect(status().isForbidden());
        mvc.perform(upload(csv, TestJwt.scanner("Nantes").roles("ADMIN").build())).andExpect(status().isForbidden());
    }

    @Test
    void structureErrorIsABadRequestProblem() throws Exception {
        mvc.perform(upload("shipmentReference,type\nA-1,PICKED_UP\n", TestJwt.scanner("Nantes").build()))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.detail").value("Missing column: site"));
    }

    @Test
    void moreDataLinesThanTheCapIsABadRequest() throws Exception {
        String csv = HEADER + "A,PICKED_UP,Nantes,2026-11-07T08:00:00Z\n".repeat(4);
        mvc.perform(upload(csv, TestJwt.scanner("Nantes").build()))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.detail").value("The file has more than 3 data lines"));
    }

    @Test
    void missingFilePartIsABadRequestProblem() throws Exception {
        mvc.perform(multipart("/api/scans/imports")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwt.scanner("Nantes").build()))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.detail").value("file: required part is missing"));
    }

    static MockMultipartHttpServletRequestBuilder upload(String csv, String token) {
        MockMultipartHttpServletRequestBuilder request = multipart("/api/scans/imports")
                .file(new MockMultipartFile("file", "scans.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8)));
        if (token != null) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return request;
    }
}
