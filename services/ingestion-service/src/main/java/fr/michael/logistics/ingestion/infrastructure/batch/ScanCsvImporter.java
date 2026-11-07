package fr.michael.logistics.ingestion.infrastructure.batch;

import fr.michael.logistics.ingestion.application.CsvStructureException;
import fr.michael.logistics.ingestion.application.ImportBusyException;
import fr.michael.logistics.ingestion.application.ImportInterruptedException;
import fr.michael.logistics.ingestion.application.ImportReport;
import fr.michael.logistics.ingestion.application.ScanPublicationException;
import fr.michael.logistics.ingestion.application.ScanImport;
import fr.michael.logistics.ingestion.application.ScanPublisher;
import fr.michael.logistics.ingestion.domain.ScanEvent;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.time.Duration;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Component;

/**
 * Import synchrone d'un CSV de scans : un job Spring Batch à une étape, construit à chaque import (il est lié au
 * fichier), lancé dans le thread de l'appelant. Dépôt de jobs sans ressource : pas de base, pas de reprise (on
 * réimporte). Un seul import à la fois par instance : le suivant attend son tour jusqu'à {@code ingestion.import.wait}.
 */
@Component
public class ScanCsvImporter implements ScanImport {

    static final int CHUNK_SIZE = 500;

    private final JobRepository jobRepository;
    private final JobOperator jobOperator;
    private final ScanPublisher publisher;
    private final Validator validator;
    private static final Logger LOG = LoggerFactory.getLogger(ScanCsvImporter.class);

    private final Semaphore oneAtATime = new Semaphore(1);
    private final Duration wait;
    private final int maxLines;

    @Autowired
    ScanCsvImporter(JobRepository jobRepository, JobOperator jobOperator, ScanPublisher publisher, Validator validator,
            @Value("${ingestion.import.wait:30s}") Duration wait,
            @Value("${ingestion.import.max-lines:10000}") int maxLines) {
        this.jobRepository = jobRepository;
        this.jobOperator = jobOperator;
        this.publisher = publisher;
        this.validator = validator;
        this.wait = wait;
        this.maxLines = maxLines;
    }

    ScanCsvImporter(JobRepository jobRepository, JobOperator jobOperator, ScanPublisher publisher, Duration wait,
            int maxLines) {
        this(jobRepository, jobOperator, publisher, Validation.buildDefaultValidatorFactory().getValidator(), wait,
                maxLines);
    }

    ScanCsvImporter(JobRepository jobRepository, JobOperator jobOperator, ScanPublisher publisher) {
        this(jobRepository, jobOperator, publisher, Validation.buildDefaultValidatorFactory().getValidator(),
                Duration.ofSeconds(30), 10_000);
    }

    /**
     * @param content octets du fichier
     * @param site site du SCANNER : chaque ligne doit être de ce site
     * @throws fr.michael.logistics.ingestion.application.CsvStructureException avant toute publication
     * @throws ImportBusyException si un autre import tourne
     * @throws ImportInterruptedException si une publication échoue (lignes déjà publiées dans l'exception)
     */
    @Override
    public ImportReport importCsv(byte[] content, String site) {
        CsvFile file = CsvFile.parse(content);
        if (file.dataLines() > maxLines) {
            throw new CsvStructureException("The file has more than " + maxLines + " data lines");
        }
        if (!acquire()) {
            throw new ImportBusyException(wait);
        }
        long start = System.nanoTime();
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
            ImportReport report = progress.report();
            LOG.atInfo()
                    .addKeyValue("lines", report.lines())
                    .addKeyValue("published", report.published())
                    .addKeyValue("rejected", report.rejected())
                    .addKeyValue("site", site)
                    .addKeyValue("durationMs", Duration.ofNanos(System.nanoTime() - start).toMillis())
                    .log("scan import finished");
            return report;
        } finally {
            oneAtATime.release();
        }
    }

    /** Attend son tour jusqu'à {@code wait} : le dépôt de jobs ne tient pas deux exécutions simultanées. */
    private boolean acquire() {
        try {
            return oneAtATime.tryAcquire(wait.toNanos(), TimeUnit.NANOSECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
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
        // le nombre de colonnes est contrôlé par le LineMapper, avec un message lisible
        FlatFileItemReader<ScanRow> reader = new FlatFileItemReader<>(
                new ByteArrayResource(file.text().getBytes(StandardCharsets.UTF_8)), lineMapper(file));
        reader.setLinesToSkip(1);
        reader.setComments(new String[0]); // une référence peut commencer par '#'
        reader.setRecordSeparatorPolicy(new QuotedRecordSeparatorPolicy());
        reader.setName("scanCsvReader");
        ItemWriter<ScanEvent> writer = chunk -> {
            List<ScanEvent> events = List.copyOf(chunk.getItems());
            publisher.publishAll(events);
            progress.chunkPublished(events.size());
        };
        return new StepBuilder("publishScans", jobRepository)
                .<ScanRow, ScanEvent>chunk(CHUNK_SIZE)
                .reader(reader)
                .processor(new ScanRowProcessor(site, progress, validator))
                .writer(writer)
                .faultTolerant()
                .skipPolicy((throwable, count) -> throwable instanceof RowRejected)
                .skipListener(progress)
                .build();
    }

    /** Numéro de la première ligne physique de l'enregistrement, en-tête compris ; une ligne vide donne une sentinelle, le processeur l'écarte. */
    private static LineMapper<ScanRow> lineMapper(CsvFile file) {
        return (line, lastLine) -> {
            int lineNumber = lastLine - (int) line.chars().filter(c -> c == '\n').count();
            if (line.isBlank()) {
                return new ScanRow(lineNumber, Map.of(), null);
            }
            List<String> cells = CsvCells.split(line);
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
