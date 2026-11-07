package fr.michael.logistics.ingestion.infrastructure.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.michael.logistics.ingestion.TestcontainersConfiguration;
import fr.michael.logistics.ingestion.application.CsvStructureException;
import fr.michael.logistics.ingestion.application.ImportAbortedException;
import fr.michael.logistics.ingestion.application.ImportBusyException;
import fr.michael.logistics.ingestion.application.ImportInterruptedException;
import fr.michael.logistics.ingestion.application.ImportReport;
import fr.michael.logistics.ingestion.application.ScanPublicationException;
import fr.michael.logistics.ingestion.application.ScanPublisher;
import fr.michael.logistics.ingestion.domain.ScanEvent;
import fr.michael.logistics.ingestion.infrastructure.security.TestJwt;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.kafka.KafkaContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ScanCsvImporterTest {

    private static final String SITE = "Nantes";
    private static final String HEADER = "shipmentReference,type,site,occurredAt,eventId,reason";

    @DynamicPropertySource
    static void jwks(DynamicPropertyRegistry registry) {
        TestJwt.register(registry);
    }

    @Autowired
    ScanCsvImporter importer;

    @Autowired
    JobRepository jobRepository;

    @Autowired
    JobOperator jobOperator;

    @Autowired
    KafkaContainer kafka;

    @Autowired
    JsonMapper json;

    @Test
    void publishesValidRowsInFileOrderWithCrlfBomBlankLinesAndColumnsInAnyOrder() {
        String ref = "IMP-" + UUID.randomUUID();
        String csv = "﻿" + "type,site,reason,occurredAt,shipmentReference\r\n"
                + "PICKED_UP," + SITE + ",,2026-11-07T08:00:00Z," + ref + "\r\n"
                + "\r\n"
                + "   \r\n"
                + "ARRIVED_AT_SITE," + SITE + ",,2026-11-07T09:00:00+01:00," + ref + "\r\n"
                + "OUT_FOR_DELIVERY," + SITE + ",,2026-11-07T10:00:00Z," + ref + "\r\n"
                + "DELIVERY_FAILED," + SITE + ",\"Absent, porte close\",2026-11-07T11:00:00Z," + ref + "\r\n";

        ImportReport report = importer.importCsv(bytes(csv), SITE);

        assertThat(report.lines()).isEqualTo(4);
        assertThat(report.published()).isEqualTo(4);
        assertThat(report.rejected()).isZero();
        assertThat(report.errors()).isEmpty();
        List<JsonNode> messages = read(ref, 4);
        assertThat(messages).extracting(m -> m.get("type").asString())
                .containsExactly("PICKED_UP", "ARRIVED_AT_SITE", "OUT_FOR_DELIVERY", "DELIVERY_FAILED");
        assertThat(messages.get(0).has("reason")).isFalse();
        assertThat(messages.get(1).get("occurredAt").asString()).isEqualTo("2026-11-07T08:00:00Z");
        assertThat(messages.get(3).get("reason").asString()).isEqualTo("Absent, porte close");
        assertThat(messages).allSatisfy(m -> {
            assertThat(m.get("site").asString()).isEqualTo(SITE);
            assertThat(UUID.fromString(m.get("eventId").asString())).isNotNull();
        });
    }

    @Test
    void keepsAGivenEventIdAndReimportGivesTheSameIds() {
        String ref = "IMP-" + UUID.randomUUID();
        UUID given = UUID.randomUUID();
        String csv = HEADER + "\n"
                + ref + ",PICKED_UP," + SITE + ",2026-11-07T08:00:00Z," + given + ",\n"
                + ref + ",ARRIVED_AT_SITE," + SITE + ",2026-11-07T09:00:00Z,,\n";

        importer.importCsv(bytes(csv), SITE);
        importer.importCsv(bytes(csv), SITE);

        List<JsonNode> messages = read(ref, 4);
        List<String> ids = messages.stream().map(m -> m.get("eventId").asString()).toList();
        assertThat(ids.get(0)).isEqualTo(given.toString());
        assertThat(ids.get(2)).isEqualTo(given.toString());
        assertThat(ids.get(1)).isEqualTo(ids.get(3));
        assertThat(ids.get(1)).isNotEqualTo(given.toString());
    }

    @Test
    void aShipmentReferenceStartingWithHashIsARowNotAComment() {
        String ref = "#IMP-" + UUID.randomUUID();
        String csv = HEADER + "\n" + ref + ",PICKED_UP," + SITE + ",2026-11-07T08:00:00Z,,\n";

        ImportReport report = importer.importCsv(bytes(csv), SITE);

        assertThat(report.lines()).isEqualTo(1);
        assertThat(report.published()).isEqualTo(1);
        assertThat(read(ref, 1)).hasSize(1);
    }

    @Test
    void acceptsAQuotedFieldSpanningLinesAndNumbersErrorsFromTheRecordsFirstLine() {
        String ref = "IMP-" + UUID.randomUUID();
        String csv = HEADER + "\r\n"                                                          // 1
                + ref + ",PICKED_UP," + SITE + ",2026-11-07T08:00:00Z,,\r\n"                    // 2
                + ref + ",OUT_FOR_DELIVERY," + SITE + ",2026-11-07T08:30:00Z,,\r\n"             // 3
                + ref + ",DELIVERY_FAILED," + SITE + ",2026-11-07T09:00:00Z,,\"Absent,\r\n"     // 4-5
                + "porte \"\"close\"\"\"\r\n"
                + ref + ",TELEPORTED," + SITE + ",2026-11-07T10:00:00Z,,\r\n";                 // 6

        ImportReport report = importer.importCsv(bytes(csv), SITE);

        assertThat(report.lines()).isEqualTo(4);
        assertThat(report.published()).isEqualTo(3);
        assertThat(report.errors()).extracting(ImportReport.ImportError::line).containsExactly(6);
        List<JsonNode> messages = read(ref, 3);
        assertThat(messages.get(2).get("reason").asString()).isEqualTo("Absent,\nporte \"close\"");
    }

    @Test
    void countsRecordsNotPhysicalLinesAgainstTheCap() {
        ScanCsvImporter local = new ScanCsvImporter(jobRepository, jobOperator,
                new RecordingPublisher(Integer.MAX_VALUE), Duration.ofSeconds(1), 2);
        String csv = HEADER + "\n"
                + "A,DELIVERY_FAILED," + SITE + ",2026-11-07T08:00:00Z,,\"x\ny\ny\"\n"
                + "B,PICKED_UP," + SITE + ",2026-11-07T08:00:00Z,,\n";

        assertThat(local.importCsv(bytes(csv), SITE).lines()).isEqualTo(2);
    }

    @Test
    void rejectsAnUnterminatedQuotedFieldBeforePublishingAnything() {
        RecordingPublisher publisher = new RecordingPublisher(Integer.MAX_VALUE);
        ScanCsvImporter local = new ScanCsvImporter(jobRepository, jobOperator, publisher);
        String csv = HEADER + "\nA,PICKED_UP," + SITE + ",2026-11-07T08:00:00Z,,\"never closed\nB,PICKED_UP\n";

        assertThatThrownBy(() -> local.importCsv(bytes(csv), SITE))
                .isInstanceOf(CsvStructureException.class)
                .hasMessage("A quoted field is not closed");
        assertThat(publisher.batches).isEmpty();
    }

    @Test
    void trimsSpacesAroundUnquotedCellsAndKeepsThoseInsideQuotes() {
        String ref = "IMP-" + UUID.randomUUID();
        String csv = HEADER + "\n"
                + " " + ref + " , PICKED_UP ," + SITE + " , 2026-11-07T08:00:00Z ,  ,\n"
                + ref + ",DELIVERY_FAILED," + SITE + ",2026-11-07T09:00:00Z,,  \" absent \"  \n";

        ImportReport report = importer.importCsv(bytes(csv), SITE);

        assertThat(report.errors()).isEmpty();
        assertThat(report.published()).isEqualTo(2);
        List<JsonNode> messages = read(ref, 2);
        assertThat(messages.get(0).get("type").asString()).isEqualTo("PICKED_UP");
        assertThat(messages.get(1).get("reason").asString()).isEqualTo(" absent ");
    }

    @Test
    void skipsInvalidRowsAndReportsThemWithTheirPhysicalLineNumber() {
        String ref = "IMP-" + UUID.randomUUID();
        String csv = HEADER + "\n"
                + ref + ",PICKED_UP," + SITE + ",2026-11-07T08:00:00Z,,\n"                  // 2 ok
                + ref + ",TELEPORTED," + SITE + ",2026-11-07T08:00:00Z,,\n"                 // 3
                + "\n"                                                                      // 4 vide
                + ref + ",ARRIVED_AT_SITE,Lyon,2026-11-07T08:00:00Z,,\n"                    // 5
                + ref + ",ARRIVED_AT_SITE," + SITE + ",,,\n"                                // 6
                + ref + ",ARRIVED_AT_SITE," + SITE + ",2026-11-07T08:00:00Z,,too early\n"    // 7
                + ref + ",PICKED_UP," + SITE + "\n"                                         // 8
                + ref + ",ARRIVED_AT_SITE," + SITE + ",2026-11-07T09:00:00Z,not-a-uuid,\n"   // 9
                + ref + ",ARRIVED_AT_SITE," + SITE + ",2026-11-07T10:00:00Z,,\n";           // 10 ok

        ImportReport report = importer.importCsv(bytes(csv), SITE);

        assertThat(report.lines()).isEqualTo(8);
        assertThat(report.published()).isEqualTo(2);
        assertThat(report.rejected()).isEqualTo(6);
        assertThat(report.errors()).extracting(ImportReport.ImportError::line)
                .containsExactly(3, 5, 6, 7, 8, 9);
        assertThat(report.errors()).extracting(ImportReport.ImportError::detail).containsExactly(
                "type: Invalid value for type",
                "site: must match the token site",
                "occurredAt: must not be null",
                "reasonAllowed: reason is only allowed with DELIVERY_FAILED",
                "line: expected 6 columns, found 3",
                "eventId: Invalid value for eventId");
        assertThat(read(ref, 2)).hasSize(2);
    }

    @Test
    void validatesEachRowWithTheSameConstraintsAsThePostScansRoute() {
        String csv = HEADER + "\n"
                + "A".repeat(51) + ",PICKED_UP," + SITE + ",2026-11-07T08:00:00Z,,\n"
                + "A,DELIVERY_FAILED," + SITE + ",2026-11-07T08:00:00Z,, \n"
                + "A,DELIVERY_FAILED," + SITE + ",2026-11-07T08:00:00Z,," + "x".repeat(201) + "\n";

        ImportReport report = importer.importCsv(bytes(csv), SITE);

        assertThat(report.errors()).extracting(ImportReport.ImportError::detail).containsExactly(
                "shipmentReference: size must be between 0 and 50",
                "reason: must not be blank",
                "reasonShortEnough: must not exceed 200 characters");
    }

    @Test
    void keepsTheExactRejectedTotalBeyondOneHundredDetailedErrors() {
        StringBuilder csv = new StringBuilder(HEADER).append('\n');
        for (int i = 0; i < 150; i++) {
            csv.append("R,NOPE,").append(SITE).append(",2026-11-07T08:00:00Z,,\n");
        }

        ImportReport report = importer.importCsv(bytes(csv.toString()), SITE);

        assertThat(report.lines()).isEqualTo(150);
        assertThat(report.rejected()).isEqualTo(150);
        assertThat(report.published()).isZero();
        assertThat(report.errors()).hasSize(100);
        assertThat(report.errors().get(99).line()).isEqualTo(101);
    }

    static List<Arguments> structureErrors() {
        return List.of(
                new Arguments(new byte[0], "The file is empty"),
                new Arguments(bytes("﻿ \n"), "The file is empty"),
                new Arguments(bytes("\nPICKED_UP\n"), "The header line is missing"),
                new Arguments(bytes("shipmentReference,type,site\nR,PICKED_UP,Nantes\n"), "Missing column: occurredAt"),
                new Arguments(bytes(HEADER + ",colour\nR,PICKED_UP,Nantes,2026-11-07T08:00:00Z,,,red\n"),
                        "Unknown column: colour"),
                new Arguments(bytes("type,type,site,occurredAt,shipmentReference\n"), "Duplicate column: type"),
                new Arguments(new byte[] {'t', 'y', 'p', 'e', '\n', (byte) 0xE9}, "The file is not valid UTF-8"));
    }

    record Arguments(byte[] content, String message) {
    }

    @ParameterizedTest
    @MethodSource("structureErrors")
    void rejectsStructureErrorsBeforePublishingAnything(Arguments arguments) {
        RecordingPublisher publisher = new RecordingPublisher(Integer.MAX_VALUE);
        ScanCsvImporter local = new ScanCsvImporter(jobRepository, jobOperator, publisher);

        assertThatThrownBy(() -> local.importCsv(arguments.content(), SITE))
                .isInstanceOf(CsvStructureException.class)
                .hasMessage(arguments.message());
        assertThat(publisher.batches).isEmpty();
    }

    @Test
    void stopsAtTheFailedChunkAndReportsTheChunksFullyAcknowledged() {
        RecordingPublisher publisher = new RecordingPublisher(2);
        ScanCsvImporter local = new ScanCsvImporter(jobRepository, jobOperator, publisher);
        StringBuilder csv = new StringBuilder(HEADER).append('\n');
        IntStream.range(0, 1200).forEach(i -> csv.append("R-").append(i).append(",PICKED_UP,").append(SITE)
                .append(",2026-11-07T08:00:00Z,,\n"));

        assertThatThrownBy(() -> local.importCsv(bytes(csv.toString()), SITE))
                .isInstanceOfSatisfying(ImportInterruptedException.class,
                        e -> assertThat(e.published()).isEqualTo(500))
                .hasRootCauseInstanceOf(ScanPublicationException.class);
        assertThat(publisher.batches).hasSize(2);
        assertThat(publisher.batches.get(0)).hasSize(500);
    }

    @Test
    void aSecondSimultaneousImportWaitsItsTurnThenIsRefusedWithRetryAfter() throws Exception {
        BlockingPublisher publisher = new BlockingPublisher();
        ScanCsvImporter local = new ScanCsvImporter(jobRepository, jobOperator, publisher, Duration.ofMillis(300), 10_000);
        String csv = HEADER + "\nW-1,PICKED_UP," + SITE + ",2026-11-07T08:00:00Z,,\n";
        CompletableFuture<ImportReport> first = CompletableFuture.supplyAsync(() -> local.importCsv(bytes(csv), SITE));
        assertThat(publisher.entered.await(10, TimeUnit.SECONDS)).isTrue();

        assertThatThrownBy(() -> local.importCsv(bytes(csv), SITE))
                .isInstanceOfSatisfying(ImportBusyException.class,
                        e -> assertThat(e.retryAfter()).isEqualTo(Duration.ofMillis(300)));

        publisher.release.countDown();
        assertThat(first.get(10, TimeUnit.SECONDS).published()).isEqualTo(1);
    }

    @Test
    void aSecondSimultaneousImportRunsOnceTheFirstIsDoneWithinTheWait() throws Exception {
        BlockingPublisher publisher = new BlockingPublisher();
        ScanCsvImporter local = new ScanCsvImporter(jobRepository, jobOperator, publisher, Duration.ofSeconds(20), 10_000);
        String csv = HEADER + "\nW-2,PICKED_UP," + SITE + ",2026-11-07T08:00:00Z,,\n";
        CompletableFuture<ImportReport> first = CompletableFuture.supplyAsync(() -> local.importCsv(bytes(csv), SITE));
        assertThat(publisher.entered.await(10, TimeUnit.SECONDS)).isTrue();
        CompletableFuture<ImportReport> second = CompletableFuture.supplyAsync(() -> local.importCsv(bytes(csv), SITE));

        publisher.release.countDown();

        assertThat(first.get(10, TimeUnit.SECONDS).published()).isEqualTo(1);
        assertThat(second.get(10, TimeUnit.SECONDS).published()).isEqualTo(1);
    }

    @Test
    void aThreadInterruptedWhileWaitingForItsTurnIsNotReportedAsBusy() throws Exception {
        BlockingPublisher publisher = new BlockingPublisher();
        ScanCsvImporter local = new ScanCsvImporter(jobRepository, jobOperator, publisher, Duration.ofSeconds(20), 10_000);
        String csv = HEADER + "\nW-3,PICKED_UP," + SITE + ",2026-11-07T08:00:00Z,,\n";
        CompletableFuture<ImportReport> first = CompletableFuture.supplyAsync(() -> local.importCsv(bytes(csv), SITE));
        assertThat(publisher.entered.await(10, TimeUnit.SECONDS)).isTrue();
        CompletableFuture<Boolean> second = new CompletableFuture<>();
        Thread waiting = new Thread(() -> {
            try {
                local.importCsv(bytes(csv), SITE);
                second.complete(false);
            } catch (ImportAbortedException e) {
                second.complete(Thread.currentThread().isInterrupted());
            } catch (Throwable e) {
                second.completeExceptionally(e);
            }
        });
        waiting.start();
        Thread.sleep(300);

        waiting.interrupt();

        assertThat(second.get(10, TimeUnit.SECONDS)).as("flag d'interruption rétabli").isTrue();
        publisher.release.countDown();
        assertThat(first.get(10, TimeUnit.SECONDS).published()).isEqualTo(1);
    }

    /** Publieur qui bloque le premier lot jusqu'à {@code release}. */
    static final class BlockingPublisher implements ScanPublisher {

        final CountDownLatch entered = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);

        @Override
        public void publish(ScanEvent event) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void publishAll(List<ScanEvent> events) {
            entered.countDown();
            try {
                release.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /** Publieur de test : accuse les lots jusqu'au {@code failingCall}-ième, qui échoue. */
    static final class RecordingPublisher implements ScanPublisher {

        final List<List<ScanEvent>> batches = new ArrayList<>();
        private final int failingCall;

        RecordingPublisher(int failingCall) {
            this.failingCall = failingCall;
        }

        @Override
        public void publish(ScanEvent event) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void publishAll(List<ScanEvent> events) {
            batches.add(events);
            if (batches.size() == failingCall) {
                throw new ScanPublicationException("simulated outage", null);
            }
        }
    }

    private static byte[] bytes(String text) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(text.getBytes(StandardCharsets.UTF_8));
        return out.toByteArray();
    }

    private List<JsonNode> read(String reference, int expected) {
        Map<String, Object> config = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "test-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        List<JsonNode> found = new ArrayList<>();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(config)) {
            consumer.subscribe(List.of("scan-events"));
            long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
            while (found.size() < expected && System.nanoTime() < deadline) {
                for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(500))) {
                    if (reference.equals(record.key())) {
                        found.add(json.readTree(record.value()));
                    }
                }
            }
        }
        return found;
    }
}
