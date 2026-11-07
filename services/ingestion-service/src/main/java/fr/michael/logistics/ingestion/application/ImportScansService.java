package fr.michael.logistics.ingestion.application;

import org.springframework.stereotype.Service;

/** Cas d'usage : importer un fichier CSV de scans pour le site d'un SCANNER. */
@Service
public class ImportScansService {

    private final ScanImport scanImport;

    public ImportScansService(ScanImport scanImport) {
        this.scanImport = scanImport;
    }

    public ImportReport importScans(byte[] content, String site) {
        return scanImport.importCsv(content, site);
    }
}
