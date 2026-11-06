package fr.michael.logistics.ai.application;

import fr.michael.logistics.ai.domain.DocumentFormat;
import fr.michael.logistics.ai.domain.TokenUsage;
import java.math.BigDecimal;

/** Port : trace et cumule ce que coûtent les appels facturés au modèle (#10) : métriques et ligne de journal. */
public interface ExtractionMetrics {

    /** Un appel facturé : {@code extracted} faux quand la réponse du modèle était inutilisable (422). */
    void recordBilledCall(DocumentFormat format, TokenUsage usage, BigDecimal costUsd, boolean extracted);
}
