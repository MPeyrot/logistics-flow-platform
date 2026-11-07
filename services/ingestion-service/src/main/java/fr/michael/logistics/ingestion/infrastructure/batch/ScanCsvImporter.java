package fr.michael.logistics.ingestion.infrastructure.batch;

import fr.michael.logistics.ingestion.application.ImportBusyException;
import fr.michael.logistics.ingestion.application.ImportInterruptedException;
import fr.michael.logistics.ingestion.application.ImportReport;
import fr.michael.logistics.ingestion.application.ScanPublicationException;
import fr.michael.logistics.ingestion.application.ScanPublisher;
import fr.michael.logistics.ingestion.domain.ScanEvent;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.batch.infrastructure.item.file.FlatFileItemReader;
import org.springframework.batch.infrastructure.item.file.LineMapper;
import org.springframework.batch.infrastructure.item.file.transform.DelimitedLineTokenizer;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Component;

/**
 * Import synchrone d'un CSV de scans : un job Spring Batch à une étape, construit à chaque import (il est lié au
 * fichier), lancé dans le thread de l'appelant. Dépôt de jobs sans ressource : pas de base, pas de reprise (on
 * réimporte). Un seul import à la fois par instance : le dépôt ne tient pas deux exécutions simultanées.
 */
@Component
public class ScanCsvImporter {

    static final int CHUNK_SIZE = 500;

    private final JobRepository jobRepository;
    private final JobOperator jobOperator;
    private final ScanPublisher publisher;
    private final Semaphore oneAtATime = new Semaphore(1);

    ScanCsvImporter(JobRepository jobRepository, JobOperator jobOperator, ScanPublisher publisher) {
        this.jobRepository = jobRepository;
        this.jobOperator = jobOperator;
        this.publisher = publisher;
    }

    /**
     * @param content octets du fichier
     * @param site site du SCANNER : chaque ligne doit être de ce site
     * @throws fr.michael.logistics.ingestion.application.CsvStructureException avant toute publication
     * @throws ImportBusyException si un autre import tourne
     * @throws ImportInterruptedException si une publication échoue (lignes déjà publiées dans l'exception)
     */
    public ImportReport importCsv(byte[] content, String site) {
        CsvFile file = CsvFile.parse(content);
        if (!oneAtATime.tryAcquire()) {
            throw new ImportBusyException();
        }
        try {
            ImportProgress progress = new ImportProgress();
            Job job = new JobBuilder("scanCsvImport", jobRepository)
                    .start(step(file, site, progress))
                    .build();
            JobParameters parameters = new JobParametersBuilder()
                    .addString("run", UUID.randomUUID().toString())
                    .toJobParameters();
            JobExecution execution = run(job, parameters);
            if (execution.getStatus() != BatchStatus.COMPLETED) {
                throw failure(execution, progress);
            }
            return progress.report();
        } finally {
            oneAtATime.release();
        }
    }

    private JobExecution run(Job job, JobParameters parameters) {
        try {
            return jobOperator.start(job, parameters);
        } catch (Exception e) {
            throw new IllegalStateException("Could not start the import job", e);
        }
    }

    private Step step(CsvFile file, String site, ImportProgress progress) {
        DelimitedLineTokenizer tokenizer = new DelimitedLineTokenizer();
        // sans noms : le nombre de colonnes est contrôlé par le LineMapper, avec un message lisible
        FlatFileItemReader<ScanRow> reader = new FlatFileItemReader<>(
                new ByteArrayResource(file.text().getBytes(StandardCharsets.UTF_8)), lineMapper(tokenizer, file));
        reader.setLinesToSkip(1);
        reader.setName("scanCsvReader");
        ItemWriter<ScanEvent> writer = chunk -> {
            List<ScanEvent> events = List.copyOf(chunk.getItems());
            publisher.publishAll(events);
            progress.chunkPublished(events.size());
        };
        return new StepBuilder("publishScans", jobRepository)
                .<ScanRow, ScanEvent>chunk(CHUNK_SIZE)
                .reader(reader)
                .processor(new ScanRowProcessor(site, progress))
                .writer(writer)
                .faultTolerant()
                .skipPolicy((throwable, count) -> throwable instanceof RowRejected)
                .skipListener(progress)
                .build();
    }

    /** Numéro de ligne physique en-tête compris ; une ligne vide donne une sentinelle, le processeur l'écarte. */
    private static LineMapper<ScanRow> lineMapper(DelimitedLineTokenizer tokenizer, CsvFile file) {
        return (line, lineNumber) -> {
            if (line.isBlank()) {
                return new ScanRow(lineNumber, Map.of(), null);
            }
            List<String> cells;
            try {
                cells = List.of(tokenizer.tokenize(line).getValues());
            } catch (RuntimeException e) {
                return new ScanRow(lineNumber, Map.of(), "line: expected " + file.columns().size() + " columns");
            }
            if (cells.size() != file.columns().size()) {
                return new ScanRow(lineNumber, Map.of(),
                        "line: expected " + file.columns().size() + " columns, found " + cells.size());
            }
            Map<String, String> values = new HashMap<>();
            for (int i = 0; i < cells.size(); i++) {
                values.put(file.columns().get(i), cells.get(i));
            }
            return new ScanRow(lineNumber, values, null);
        };
    }

    private static RuntimeException failure(JobExecution execution, ImportProgress progress) {
        for (Throwable failure : execution.getAllFailureExceptions()) {
            for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
                if (cause instanceof ScanPublicationException) {
                    return new ImportInterruptedException(progress.published(), cause);
                }
            }
        }
        Throwable first = execution.getAllFailureExceptions().stream().findFirst().orElse(null);
        return new IllegalStateException("The import job ended with status " + execution.getStatus(), first);
    }
}
