package fr.michael.logistics.ingestion.infrastructure.batch;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import fr.michael.logistics.ingestion.application.CsvStructureException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class CsvFileTest {

    private static final String HEADER = "shipmentReference,type,site,occurredAt";

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void anUnclosedQuoteFollowedByAMillionNewlinesIsRejectedInBoundedTime() {
        String csv = HEADER + "\n\"" + "\n".repeat(1_000_000);

        assertTimeoutPreemptively(Duration.ofSeconds(5), () ->
                assertThatThrownBy(() -> CsvFile.parse(bytes(csv)))
                        .isInstanceOf(CsvStructureException.class)
                        .hasMessage("A quoted field is not closed"));
    }
}
