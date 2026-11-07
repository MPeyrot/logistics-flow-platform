package fr.michael.logistics.ingestion.application;

/** Le fichier CSV est inutilisable (fichier vide, en-tête, colonnes, encodage) : rien n'a été publié. */
public class CsvStructureException extends RuntimeException {

    public CsvStructureException(String message) {
        super(message);
    }
}
