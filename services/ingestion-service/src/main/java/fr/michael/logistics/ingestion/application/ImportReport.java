package fr.michael.logistics.ingestion.application;

import java.util.List;

/**
 * Bilan d'un import. {@code lines} : lignes de données non vides ; {@code errors} : les premières erreurs seulement,
 * {@code rejected} est toujours le total exact.
 */
public record ImportReport(int lines, int published, int rejected, List<ImportError> errors) {

    /** Nombre maximal d'erreurs détaillées. */
    public static final int MAX_ERRORS = 100;

    /** Une ligne rejetée : numéro de ligne physique (en-tête = 1) et {@code champ: message}. */
    public record ImportError(int line, String detail) {
    }
}
