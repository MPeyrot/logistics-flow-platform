package fr.michael.logistics.ingestion.infrastructure.batch;

import fr.michael.logistics.ingestion.application.ImportReport;
import fr.michael.logistics.ingestion.application.ImportReport.ImportError;
import java.util.ArrayList;
import java.util.List;
import org.springframework.batch.core.listener.SkipListener;

/** Compteurs d'un import (un seul thread, un objet par import). */
class ImportProgress implements SkipListener<ScanRow, Object> {

    private int lines;
    private int published;
    private int rejected;
    private final List<ImportError> errors = new ArrayList<>();

    void lineSeen() {
        lines++;
    }

    void chunkPublished(int count) {
        published += count;
    }

    int published() {
        return published;
    }

    @Override
    public void onSkipInProcess(ScanRow row, Throwable t) {
        rejected++;
        if (errors.size() < ImportReport.MAX_ERRORS) {
            errors.add(new ImportError(row.line(), t.getMessage()));
        }
    }

    ImportReport report() {
        return new ImportReport(lines, published, rejected, List.copyOf(errors));
    }
}
