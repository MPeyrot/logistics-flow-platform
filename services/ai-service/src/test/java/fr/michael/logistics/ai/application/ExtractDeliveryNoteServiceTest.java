package fr.michael.logistics.ai.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.michael.logistics.ai.domain.DeliveryNote;
import fr.michael.logistics.ai.domain.DocumentFormat;
import fr.michael.logistics.ai.domain.ModelPricing;
import fr.michael.logistics.ai.domain.SourceDocument;
import fr.michael.logistics.ai.domain.TokenUsage;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ExtractDeliveryNoteServiceTest {

    private final ModelPricing pricing = new ModelPricing(
            new BigDecimal("4.00"), new BigDecimal("20.00"), new BigDecimal("5.00"), new BigDecimal("0.20"));
    private final ExtractionQuota quota = new ExtractionQuota(100, java.time.Clock.systemUTC());
    private final SourceDocument document = new SourceDocument("%PDF-1.4".getBytes(), DocumentFormat.PDF);

    @Test
    void returnsNoteWithUsageAndCost() {
        var usage = new TokenUsage(1000, 100, 0, 0);
        var note = new DeliveryNote("BL-1", null, null, null, null, null, null);
        var service = new ExtractDeliveryNoteService(doc -> new ModelExtraction(note, usage), pricing, quota);

        ExtractionResult result = service.extract(document);

        assertThat(result.note()).isEqualTo(note);
        assertThat(result.usage()).isEqualTo(usage);
        assertThat(result.costUsd()).isEqualByComparingTo("0.006000");
    }

    @Test
    void propagatesUnreadableDocument() {
        var service = new ExtractDeliveryNoteService(doc -> {
            throw new UnreadableDocumentException("no reference", new TokenUsage(10, 5, 0, 0));
        }, pricing, quota);

        assertThatThrownBy(() -> service.extract(document)).isInstanceOf(UnreadableDocumentException.class);
    }
}
