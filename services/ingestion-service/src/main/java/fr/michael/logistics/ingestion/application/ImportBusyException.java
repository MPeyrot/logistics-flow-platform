package fr.michael.logistics.ingestion.application;

/** Un autre import tourne déjà dans cette instance. */
public class ImportBusyException extends RuntimeException {

    public ImportBusyException() {
        super("Another import is already running");
    }
}
