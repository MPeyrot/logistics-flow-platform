package fr.michael.logistics.ingestion.application;

/** L'import a échoué pour une raison imprévue. {@code published} : lignes des chunks entièrement accusés. */
public class ImportFailedException extends RuntimeException {

    private final int published;

    public ImportFailedException(int published, Throwable cause) {
        super("Import failed after " + published + " published scans", cause);
        this.published = published;
    }

    public int published() {
        return published;
    }
}
