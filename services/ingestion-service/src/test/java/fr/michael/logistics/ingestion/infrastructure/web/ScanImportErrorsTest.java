package fr.michael.logistics.ingestion.infrastructure.web;

import static fr.michael.logistics.ingestion.infrastructure.web.ScanImportApiTest.upload;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import fr.michael.logistics.ingestion.TestcontainersConfiguration;
import fr.michael.logistics.ingestion.application.ImportBusyException;
import fr.michael.logistics.ingestion.application.ImportFailedException;
import fr.michael.logistics.ingestion.application.ImportInterruptedException;
import fr.michael.logistics.ingestion.application.ScanPublicationException;
import fr.michael.logistics.ingestion.infrastructure.batch.ScanCsvImporter;
import fr.michael.logistics.ingestion.infrastructure.security.TestJwt;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Le traducteur d'erreurs de la route, avec un importeur qui échoue. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ScanImportErrorsTest {

    private static final String CSV = "shipmentReference,type,site,occurredAt\nA-1,PICKED_UP,Nantes,2026-11-07T08:00:00Z\n";

    @DynamicPropertySource
    static void jwks(DynamicPropertyRegistry registry) {
        TestJwt.register(registry);
    }

    @Autowired
    MockMvc mvc;

    @MockitoBean
    ScanCsvImporter importer;

    @Test
    void anUnexpectedJobFailureIsAnInternalErrorThatStillCarriesThePublishedCount() throws Exception {
        when(importer.importCsv(any(), eq("Nantes")))
                .thenThrow(new ImportFailedException(500, new IllegalStateException("bug")));

        mvc.perform(upload(CSV, TestJwt.scanner("Nantes").build()))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.published").value(500));
    }

    @Test
    void kafkaOutageIsServiceUnavailableWithThePublishedCount() throws Exception {
        when(importer.importCsv(any(), eq("Nantes")))
                .thenThrow(new ImportInterruptedException(500, new ScanPublicationException("down", null)));

        mvc.perform(upload(CSV, TestJwt.scanner("Nantes").build()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.published").value(500))
                .andExpect(jsonPath("$.detail").value("Import interrupted: Kafka is unavailable, retry later"));
    }

    @Test
    void busyImporterIsTooManyRequestsWithRetryAfter() throws Exception {
        when(importer.importCsv(any(), eq("Nantes"))).thenThrow(new ImportBusyException(Duration.ofSeconds(30)));

        mvc.perform(upload(CSV, TestJwt.scanner("Nantes").build()))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "30"))
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
    }
}
