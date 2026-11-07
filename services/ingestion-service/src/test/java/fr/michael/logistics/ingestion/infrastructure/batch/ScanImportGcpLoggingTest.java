package fr.michael.logistics.ingestion.infrastructure.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.michael.logistics.ingestion.TestcontainersConfiguration;
import fr.michael.logistics.ingestion.application.ImportInterruptedException;
import fr.michael.logistics.ingestion.infrastructure.security.TestJwt;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Avec le profil {@code gcp}, la fin d'un import écrit une ligne JSON aux champs de premier niveau. */
@SpringBootTest
@ActiveProfiles("gcp")
@ExtendWith(OutputCaptureExtension.class)
@Import(TestcontainersConfiguration.class)
class ScanImportGcpLoggingTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    static {
        // Les contextes des autres classes ont déjà configuré la journalisation de la JVM.
        LoggingSystem.get(ScanImportGcpLoggingTest.class.getClassLoader()).cleanUp();
    }

    @DynamicPropertySource
    static void jwks(DynamicPropertyRegistry registry) {
        TestJwt.register(registry);
    }

    @AfterAll
    static void cleanUp() {
        LoggingSystem.get(ScanImportGcpLoggingTest.class.getClassLoader()).cleanUp();
    }

    @Autowired
    ScanCsvImporter importer;

    @Autowired
    JobRepository jobRepository;

    @Autowired
    JobOperator jobOperator;

    @Test
    void anInterruptedImportAlsoWritesOneLineWithItsOutcomeAndPublishedCount(CapturedOutput output) {
        ScanCsvImporter local = new ScanCsvImporter(jobRepository, jobOperator,
                new ScanCsvImporterTest.RecordingPublisher(1));
        String csv = "shipmentReference,type,site,occurredAt\n"
                + "SECRET-REF-1,PICKED_UP,Nantes,2026-11-07T08:00:00Z\n";

        assertThatThrownBy(() -> local.importCsv(csv.getBytes(StandardCharsets.UTF_8), "Nantes"))
                .isInstanceOf(ImportInterruptedException.class);

        JsonNode line = Arrays.stream(output.getAll().split("\n"))
                .filter(l -> l.startsWith("{") && l.contains("scan import finished"))
                .map(JSON::readTree)
                .findFirst().orElseThrow(() -> new AssertionError("aucune ligne JSON d'import dans : " + output.getAll()));
        assertThat(line.get("severity").asString()).isEqualTo("WARNING");
        assertThat(line.get("outcome").asString()).isEqualTo("interrupted");
        assertThat(line.get("published").asInt()).isZero();
        assertThat(line.get("lines").asInt()).isEqualTo(1);
        assertThat(line.get("site").asString()).isEqualTo("Nantes");
        assertThat(output.getAll()).doesNotContain("SECRET-REF");
    }

    @Test
    void finishedImportIsOneJsonLineWithoutRowContent(CapturedOutput output) {
        String csv = "shipmentReference,type,site,occurredAt\n"
                + "SECRET-REF-1,PICKED_UP,Nantes,2026-11-07T08:00:00Z\n"
                + "SECRET-REF-2,NOPE,Nantes,2026-11-07T08:00:00Z\n";

        importer.importCsv(csv.getBytes(StandardCharsets.UTF_8), "Nantes");

        JsonNode line = Arrays.stream(output.getAll().split("\n"))
                .filter(l -> l.startsWith("{") && l.contains("scan import finished"))
                .map(JSON::readTree)
                .findFirst().orElseThrow(() -> new AssertionError("aucune ligne JSON d'import dans : " + output.getAll()));
        assertThat(line.get("severity").asString()).isEqualTo("INFO");
        assertThat(line.get("outcome").asString()).isEqualTo("completed");
        assertThat(line.get("lines").asInt()).isEqualTo(2);
        assertThat(line.get("published").asInt()).isEqualTo(1);
        assertThat(line.get("rejected").asInt()).isEqualTo(1);
        assertThat(line.get("site").asString()).isEqualTo("Nantes");
        assertThat(line.get("durationMs").isNumber()).isTrue();
        assertThat(output.getAll()).doesNotContain("SECRET-REF");
    }
}
