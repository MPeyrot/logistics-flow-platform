package fr.michael.logistics.ingestion.infrastructure.batch;

import java.util.Map;

/**
 * Ligne lue, avec son numéro de ligne physique (en-tête = 1). {@code values} vide : ligne vide, à ignorer.
 * {@code structureError} : ligne qui n'a pas le bon nombre de colonnes, rejetée par le processeur.
 */
record ScanRow(int line, Map<String, String> values, String structureError) {

    boolean isBlank() {
        return values.isEmpty() && structureError == null;
    }
}
