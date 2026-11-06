package fr.michael.logistics.ai.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.michael.logistics.ai.domain.DeliveryNote;
import fr.michael.logistics.ai.domain.DocumentFormat;
import fr.michael.logistics.ai.domain.ModelPricing;
import fr.michael.logistics.ai.domain.RateCard;
import fr.michael.logistics.ai.domain.SourceDocument;
import fr.michael.logistics.ai.domain.TokenUsage;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ExtractDeliveryNoteServiceTest {

    private final ModelPricing pricing = new ModelPricing(new RateCard(
            new BigDecimal("4.00"), new BigDecimal("20.00"), new BigDecimal("5.00"), new BigDecimal("0.20")));
    private final ExtractionQuota quota = new ExtractionQuota(100, java.time.Clock.systemUTC());
    private final DailyBudget budget = new DailyBudget(new BigDecimal("1"), java.time.Clock.systemUTC());
    private final java.util.List<String> billed = new java.util.ArrayList<>();
    private final ExtractionMetrics metrics = (usage, cost, extracted) ->
            billed.add((extracted ? "extracted " : "unreadable ") + cost.toPlainString());
    private final SourceDocument document = new SourceDocument("%PDF-1.4".getBytes(), DocumentFormat.PDF);

    @Test
    void returnsNoteWithUsageAndCost() {
        var usage = new TokenUsage(1000, 100, 0, 0);
        var note = new DeliveryNote("BL-1", null, null, null, null, null, null);
        var service = new ExtractDeliveryNoteService(doc -> new ModelExtraction(note, usage), pricing, quota, budget, metrics, "claude-test");

        ExtractionResult result = service.extract(document);

        assertThat(result.note()).isEqualTo(note);
        assertThat(result.usage()).isEqualTo(usage);
        assertThat(result.costUsd()).isEqualByComparingTo("0.006000");
        assertThat(billed).containsExactly("extracted 0.006000");
    }

    @Test
    void propagatesUnreadableDocument() {
        var service = new ExtractDeliveryNoteService(doc -> {
            throw new UnreadableDocumentException("no reference", new TokenUsage(10, 5, 0, 0));
        }, pricing, quota, budget, metrics, "claude-test");

        assertThatThrownBy(() -> service.extract(document)).isInstanceOf(UnreadableDocumentException.class);
        // Réponse du modèle facturée même inutilisable : 10*4 + 5*20 = 140 micro-dollars
        assertThat(billed).containsExactly("unreadable 0.000140");
    }

    /** Clé absente (503) puis erreur de l'API sans réponse du modèle (502) : rien n'est facturé, le quota est rendu. */
    @Test
    void failuresWithoutBilledCallGiveTheQuotaBack() {
        var single = new ExtractionQuota(1, java.time.Clock.systemUTC());
        var note = new DeliveryNote("BL-1", null, null, null, null, null, null);
        var outcomes = new java.util.ArrayDeque<java.util.function.Supplier<ModelExtraction>>(java.util.List.of(
                () -> { throw new ExtractionUnavailableException("ANTHROPIC_API_KEY is not set"); },
                () -> { throw new ExtractionFailedException("Claude API call failed"); },
                () -> new ModelExtraction(note, new TokenUsage(10, 5, 0, 0))));
        var service = new ExtractDeliveryNoteService(doc -> outcomes.removeFirst().get(), pricing, single, budget, metrics, "claude-test");

        assertThatThrownBy(() -> service.extract(document)).isInstanceOf(ExtractionUnavailableException.class);
        assertThatThrownBy(() -> service.extract(document)).isInstanceOf(ExtractionFailedException.class);
        assertThat(service.extract(document).note()).isEqualTo(note);
        assertThat(billed).hasSize(1);
    }

    /** Réponse du modèle invalide (422) : l'appel a été facturé, le quota reste pris. */
    @Test
    void unreadableDocumentKeepsTheQuota() {
        var single = new ExtractionQuota(1, java.time.Clock.systemUTC());
        var service = new ExtractDeliveryNoteService(doc -> {
            throw new UnreadableDocumentException("no reference", new TokenUsage(10, 5, 0, 0));
        }, pricing, single, budget, metrics, "claude-test");

        assertThatThrownBy(() -> service.extract(document)).isInstanceOf(UnreadableDocumentException.class);
        assertThatThrownBy(() -> service.extract(document)).isInstanceOf(ExtractionQuotaExceededException.class);
    }

    /** Budget du jour consommé : 429 sans appel au modèle ni réservation du quota horaire. */
    @Test
    void refusesOnceTheDailyBudgetIsSpent() {
        var tiny = new DailyBudget(new BigDecimal("0.005"), java.time.Clock.systemUTC());
        var calls = new int[1];
        var note = new DeliveryNote("BL-1", null, null, null, null, null, null);
        var service = new ExtractDeliveryNoteService(doc -> {
            calls[0]++;
            return new ModelExtraction(note, new TokenUsage(1000, 100, 0, 0)); // 0,006 $
        }, pricing, quota, tiny, metrics, "claude-test");

        service.extract(document);

        assertThatThrownBy(() -> service.extract(document)).isInstanceOf(ExtractionQuotaExceededException.class);
        assertThat(calls[0]).isEqualTo(1);
    }

    /** Réponse inutilisable (422) : facturée, elle compte dans le budget. */
    @Test
    void unreadableResponseCountsInTheBudget() {
        var tiny = new DailyBudget(new BigDecimal("0.0001"), java.time.Clock.systemUTC());
        var service = new ExtractDeliveryNoteService(doc -> {
            throw new UnreadableDocumentException("no reference", new TokenUsage(10, 5, 0, 0)); // 0,00014 $
        }, pricing, quota, tiny, metrics, "claude-test");

        assertThatThrownBy(() -> service.extract(document)).isInstanceOf(UnreadableDocumentException.class);
        assertThatThrownBy(() -> service.extract(document)).isInstanceOf(ExtractionQuotaExceededException.class);
    }
}
