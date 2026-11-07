package fr.michael.logistics.ingestion.application;

/** La publication a échoué en cours d'import. {@code published} : lignes des chunks entièrement accusés. */
public class ImportInterruptedException extends RuntimeException {

    private final int published;

    public ImportInterruptedException(int published, Throwable cause) {
        super("Import interrupted after " + published + " published scans", cause);
        this.published = published;
    }

    public int published() {
        return published;
    }
}
