package fr.michael.logistics.ai.application;

import fr.michael.logistics.ai.domain.TokenUsage;
import java.math.BigDecimal;

/** Port : cumule ce que coûtent les appels facturés au modèle (#10). */
public interface ExtractionMetrics {

    /** Un appel facturé : {@code extracted} faux quand la réponse du modèle était inutilisable (422). */
    void recordBilledCall(TokenUsage usage, BigDecimal costUsd, boolean extracted);
}
