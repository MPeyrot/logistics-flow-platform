package fr.michael.logistics.ingestion.application;

/** Le thread de l'import a été interrompu avant son tour (arrêt de l'instance) : rien n'a été publié. */
public class ImportAbortedException extends RuntimeException {

    public ImportAbortedException() {
        super("Interrupted while waiting for another import");
    }
}
