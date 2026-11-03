package fr.michael.logistics.ai.application;

import fr.michael.logistics.ai.domain.ModelPricing;
import fr.michael.logistics.ai.domain.SourceDocument;
import java.math.BigDecimal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ExtractDeliveryNoteService {

    private static final Logger log = LoggerFactory.getLogger(ExtractDeliveryNoteService.class);

    private final DeliveryNoteExtractor extractor;
    private final ModelPricing pricing;
    private final ExtractionQuota quota;

    public ExtractDeliveryNoteService(DeliveryNoteExtractor extractor, ModelPricing pricing,
            ExtractionQuota quota) {
        this.extractor = extractor;
        this.quota = quota;
        this.pricing = pricing;
    }

    public ExtractionResult extract(SourceDocument document) {
        quota.acquire();
        ModelExtraction extraction;
        try {
            extraction = extractor.extract(document);
        } catch (UnreadableDocumentException e) {
            if (e.usage() != null) {
                log.warn("delivery note unreadable: format={} costUsd={}", document.format(),
                        pricing.costOf(e.usage()).toPlainString());
            }
            throw e;
        } catch (ExtractionUnavailableException | ExtractionFailedException e) {
            // Clé absente, ou API en erreur sans réponse du modèle : rien n'a été facturé.
            quota.release();
            throw e;
        }
        BigDecimal cost = pricing.costOf(extraction.usage());
        log.info("delivery note extracted: format={} inputTokens={} outputTokens={} cacheWriteTokens={} cacheReadTokens={} costUsd={}",
                document.format(), extraction.usage().inputTokens(), extraction.usage().outputTokens(),
                extraction.usage().cacheWriteTokens(), extraction.usage().cacheReadTokens(), cost.toPlainString());
        return new ExtractionResult(extraction.note(), extraction.usage(), cost);
    }
}
