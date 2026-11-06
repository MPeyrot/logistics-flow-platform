package fr.michael.logistics.ai.application;

import fr.michael.logistics.ai.domain.ModelPricing;
import fr.michael.logistics.ai.domain.SourceDocument;
import fr.michael.logistics.ai.domain.TokenUsage;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.stereotype.Service;

@Service
public class ExtractDeliveryNoteService {

    private final DeliveryNoteExtractor extractor;
    private final ModelPricing pricing;
    private final ExtractionQuota quota;
    private final DailyBudget budget;
    private final ExtractionMetrics metrics;

    public ExtractDeliveryNoteService(DeliveryNoteExtractor extractor, ModelPricing pricing,
            ExtractionQuota quota, DailyBudget budget, ExtractionMetrics metrics) {
        this.budget = budget;
        this.extractor = extractor;
        this.quota = quota;
        this.pricing = pricing;
        this.metrics = metrics;
    }

    public ExtractionResult extract(SourceDocument document) {
        LocalDate budgetDay = budget.checkAvailable();
        quota.acquire();
        ModelExtraction extraction;
        try {
            extraction = extractor.extract(document);
        } catch (UnreadableDocumentException e) {
            if (e.usage() != null) {
                billed(budgetDay, document, e.usage(), false);
            }
            throw e;
        } catch (ExtractionUnavailableException | ExtractionFailedException e) {
            // Clé absente, ou API en erreur sans réponse du modèle : rien n'a été facturé.
            quota.release();
            throw e;
        }
        BigDecimal cost = billed(budgetDay, document, extraction.usage(), true);
        return new ExtractionResult(extraction.note(), extraction.usage(), cost);
    }

    /** Un appel facturé : imputé au budget du jour contrôlé, puis tracé (métriques et journal). */
    private BigDecimal billed(LocalDate budgetDay, SourceDocument document, TokenUsage usage, boolean extracted) {
        BigDecimal cost = pricing.costOf(usage);
        budget.record(budgetDay, cost);
        metrics.recordBilledCall(document.format(), usage, cost, extracted);
        return cost;
    }
}
