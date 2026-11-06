package fr.michael.logistics.ai.application;

import fr.michael.logistics.ai.domain.ModelPricing;
import fr.michael.logistics.ai.domain.SourceDocument;
import java.math.BigDecimal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ExtractDeliveryNoteService {

    private static final Logger log = LoggerFactory.getLogger(ExtractDeliveryNoteService.class);

    private final DeliveryNoteExtractor extractor;
    private final ModelPricing pricing;
    private final ExtractionQuota quota;
    private final DailyBudget budget;
    private final ExtractionMetrics metrics;
    private final String model;

    public ExtractDeliveryNoteService(DeliveryNoteExtractor extractor, ModelPricing pricing,
            ExtractionQuota quota, DailyBudget budget, ExtractionMetrics metrics,
            @Value("${ai.claude.model}") String model) {
        this.model = model;
        this.budget = budget;
        this.extractor = extractor;
        this.quota = quota;
        this.pricing = pricing;
        this.metrics = metrics;
    }

    public ExtractionResult extract(SourceDocument document) {
        budget.checkAvailable();
        quota.acquire();
        ModelExtraction extraction;
        try {
            extraction = extractor.extract(document);
        } catch (UnreadableDocumentException e) {
            if (e.usage() != null) {
                BigDecimal cost = pricing.costOf(e.usage());
                budget.record(cost);
                metrics.recordBilledCall(e.usage(), cost, false);
                log.atWarn()
                        .addKeyValue("costUsd", cost)
                        .addKeyValue("model", model)
                        .addKeyValue("outcome", "unreadable")
                        .log("delivery note unreadable: format={} costUsd={}", document.format(), cost.toPlainString());
            }
            throw e;
        } catch (ExtractionUnavailableException | ExtractionFailedException e) {
            // Clé absente, ou API en erreur sans réponse du modèle : rien n'a été facturé.
            quota.release();
            throw e;
        }
        BigDecimal cost = pricing.costOf(extraction.usage());
        budget.record(cost);
        metrics.recordBilledCall(extraction.usage(), cost, true);
        log.atInfo()
                .addKeyValue("costUsd", cost)
                .addKeyValue("model", model)
                .addKeyValue("outcome", "extracted")
                .log("delivery note extracted: format={} inputTokens={} outputTokens={} cacheWriteTokens={} cacheReadTokens={} costUsd={}",
                        document.format(), extraction.usage().inputTokens(), extraction.usage().outputTokens(),
                        extraction.usage().cacheWriteTokens(), extraction.usage().cacheReadTokens(), cost.toPlainString());
        return new ExtractionResult(extraction.note(), extraction.usage(), cost);
    }
}
