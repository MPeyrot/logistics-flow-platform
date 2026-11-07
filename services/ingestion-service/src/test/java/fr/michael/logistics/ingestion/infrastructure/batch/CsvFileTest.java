package fr.michael.logistics.ingestion.infrastructure.batch;

import static org.assertj.core.api.Assertions.assertThat;
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

    @Test
    void aQuoteInTheMiddleOfAnUnquotedCellDoesNotGlueTheFollowingLines() {
        String csv = HEADER + "\nREF\"7,PICKED_UP,Nantes,2026-11-07T08:00:00Z\nB,PICKED_UP,Nantes,2026-11-07T08:00:00Z\n";

        assertThat(CsvFile.parse(bytes(csv)).dataLines()).isEqualTo(2);
    }

    @Test
    void aQuoteAfterTheClosingQuoteOfACellIsAnOrdinaryCharacter() {
        String csv = HEADER + "\n\"A\"x\"y,PICKED_UP,Nantes,2026-11-07T08:00:00Z\nB,PICKED_UP,Nantes,2026-11-07T08:00:00Z\n";

        assertThat(CsvFile.parse(bytes(csv)).dataLines()).isEqualTo(2);
    }
}
