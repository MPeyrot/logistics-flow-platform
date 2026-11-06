package fr.michael.logistics.ai.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * Budget quotidien en dollars (#59), jour UTC ; 0 coupe toutes les extractions (#72). Compteur en mémoire, exact tant qu'ai-service tourne
 * sur une seule instance (max-instances=1). Une fois atteint, plus d'appel au modèle jusqu'à minuit UTC.
 * Des appels simultanés déjà partis peuvent le dépasser de leur coût : la vérification se fait avant l'appel.
 */
public class DailyBudget {

    private final BigDecimal limit;
    private final Clock clock;
    private LocalDate day;
    private BigDecimal spent = BigDecimal.ZERO;

    public DailyBudget(BigDecimal limit, Clock clock) {
        if (limit.signum() < 0) {
            throw new IllegalArgumentException("The daily budget must not be negative");
        }
        this.limit = limit;
        this.clock = clock;
    }

    /** Lève {@link ExtractionQuotaExceededException} (retour à minuit UTC) si le budget du jour est consommé. */
    public synchronized LocalDate checkAvailable() {
        Instant now = rollOver();
        if (spent.compareTo(limit) >= 0) {
            Instant midnight = day.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
            throw new ExtractionQuotaExceededException(Math.max(1, Duration.between(now, midnight).toSeconds() + 1));
        }
        return day;
    }

    /**
     * Ajoute le coût d'un appel facturé au jour où il a été contrôlé ({@code checkedOn}, rendu par
     * {@link #checkAvailable()}) : un appel qui finit après minuit n'entame pas le budget du jour suivant.
     */
    public synchronized void record(LocalDate checkedOn, BigDecimal costUsd) {
        rollOver();
        if (checkedOn.equals(day)) {
            spent = spent.add(costUsd);
        }
    }

    private Instant rollOver() {
        Instant now = clock.instant();
        LocalDate today = now.atOffset(ZoneOffset.UTC).toLocalDate();
        if (!today.equals(day)) {
            day = today;
            spent = BigDecimal.ZERO;
        }
        return now;
    }
}
