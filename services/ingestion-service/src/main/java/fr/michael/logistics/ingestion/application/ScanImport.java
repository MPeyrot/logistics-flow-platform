package fr.michael.logistics.ingestion.application;

/** Port d'import en lot de scans depuis un fichier CSV, implémenté par l'adaptateur Spring Batch. */
public interface ScanImport {

    /**
     * @param content octets du fichier
     * @param site site du SCANNER : chaque ligne doit être de ce site
     * @throws CsvStructureException avant toute publication
     * @throws ImportBusyException si un autre import tourne
     * @throws ImportAbortedException si le thread est interrompu en attendant son tour
     * @throws ImportInterruptedException si une publication échoue (lignes déjà publiées dans l'exception)
     */
    ImportReport importCsv(byte[] content, String site);
}
