package fr.michael.logistics.ingestion.infrastructure.batch;

import java.util.Map;

/**
 * Ligne lue, avec son numéro de ligne physique (en-tête = 1); les lignes vides sont sautées à la lecture.
 * {@code structureError} : ligne qui n'a pas le bon nombre de colonnes, rejetée par le processeur.
 */
record ScanRow(int line, Map<String, String> values, String structureError) {
}
