package fr.michael.logistics.ingestion.infrastructure.batch;

/** Ligne invalide : sautée par le job et rapportée. */
class RowRejected extends RuntimeException {

    private final int line;

    RowRejected(int line, String detail) {
        super(detail);
        this.line = line;
    }

    int line() {
        return line;
    }
}
