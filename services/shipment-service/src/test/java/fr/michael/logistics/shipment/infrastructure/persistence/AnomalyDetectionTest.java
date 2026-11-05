package fr.michael.logistics.shipment.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import fr.michael.logistics.shipment.TestcontainersConfiguration;
import fr.michael.logistics.shipment.application.AnomalyRepository;
import fr.michael.logistics.shipment.application.DetectAnomaliesService;
import fr.michael.logistics.shipment.application.RecordScanService;
import fr.michael.logistics.shipment.application.ShipmentRepository;
import fr.michael.logistics.shipment.domain.Anomaly;
import fr.michael.logistics.shipment.domain.AnomalyType;
import fr.michael.logistics.shipment.domain.ScanEvent;
import fr.michael.logistics.shipment.domain.ScanType;
import fr.michael.logistics.shipment.domain.Shipment;
import fr.michael.logistics.shipment.domain.ShipmentStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.config.FixedDelayTask;
import org.springframework.scheduling.config.ScheduledTaskHolder;
import org.springframework.transaction.support.TransactionOperations;

/**
 * Détection des anomalies sur une vraie base : le cas d'usage est appelé directement, sans attendre la tâche
 * planifiée. La base est partagée avec les autres classes de test : on ne regarde que ses propres expéditions.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class AnomalyDetectionTest {

    @Autowired
    DetectAnomaliesService detection;

    @Autowired
    ShipmentRepository shipments;

    @Autowired
    AnomalyRepository anomalies;

    @Autowired
    RecordScanService scans;

    @Autowired
    List<ScheduledTaskHolder> scheduledTasks;

    @Autowired
    TransactionOperations transactions;

    @Test
    void detectsLateAndStalledShipmentsButNotFreshOrDeliveredOnes() {
        Instant now = Instant.now();
        Shipment lateAndStalled = save(ShipmentStatus.CREATED, now.minus(Duration.ofHours(49)), now.minusSeconds(3_600));
        Shipment fresh = save(ShipmentStatus.CREATED, now.minusSeconds(60), now.plus(Duration.ofDays(1)));
        Shipment legacy = save(ShipmentStatus.IN_TRANSIT, now.minus(Duration.ofHours(1)), null);
        Shipment delivered = save(ShipmentStatus.DELIVERED, now.minus(Duration.ofDays(5)), now.minus(Duration.ofDays(4)));

        detection.detect();

        assertThat(openTypes(lateAndStalled)).containsExactlyInAnyOrder(AnomalyType.LATE, AnomalyType.STALLED);
        assertThat(openTypes(fresh)).isEmpty();
        assertThat(openTypes(legacy)).isEmpty();
        assertThat(openTypes(delivered)).isEmpty();
    }

    @Test
    void repeatedDetectionKeepsASingleOpenAnomalyPerType() {
        Instant now = Instant.now();
        Shipment shipment = save(ShipmentStatus.CREATED, now.minus(Duration.ofHours(49)), now.minusSeconds(3_600));

        detection.detect();
        detection.detect();

        assertThat(openTypes(shipment)).containsExactlyInAnyOrder(AnomalyType.LATE, AnomalyType.STALLED);
    }

    @Test
    void concurrentDetectionsCreateNoDuplicateAndRaiseNoError() throws Exception {
        Instant now = Instant.now();
        Shipment shipment = save(ShipmentStatus.CREATED, now.minus(Duration.ofHours(49)), now.minusSeconds(3_600));
        CyclicBarrier start = new CyclicBarrier(2);

        CompletableFuture<Integer> first = CompletableFuture.supplyAsync(() -> detectAfter(start));
        CompletableFuture<Integer> second = CompletableFuture.supplyAsync(() -> detectAfter(start));
        CompletableFuture.allOf(first, second).get();

        assertThat(openTypes(shipment)).containsExactlyInAnyOrder(AnomalyType.LATE, AnomalyType.STALLED);
    }

    @Test
    void openingAnAlreadyOpenAnomalyIsNotAnError() {
        Instant now = Instant.now();
        Shipment shipment = save(ShipmentStatus.IN_TRANSIT, now.minusSeconds(60), now.plus(Duration.ofDays(1)));

        boolean opened = anomalies.openIfAbsent(Anomaly.open(shipment.id(), AnomalyType.LATE, now));
        boolean openedAgain = anomalies.openIfAbsent(Anomaly.open(shipment.id(), AnomalyType.LATE, now));

        assertThat(opened).isTrue();
        assertThat(openedAgain).isFalse();
        assertThat(openTypes(shipment)).containsExactly(AnomalyType.LATE);
    }

    @Test
    void scanResolvesStalledThenDeliveryResolvesLate() {
        Instant now = Instant.now();
        Shipment shipment = save(ShipmentStatus.CREATED, now.minus(Duration.ofHours(49)), now.minusSeconds(3_600));
        detection.detect();

        scan(shipment, ScanType.PICKED_UP);

        assertThat(openTypes(shipment)).containsExactly(AnomalyType.LATE);

        scan(shipment, ScanType.OUT_FOR_DELIVERY);
        scan(shipment, ScanType.DELIVERED);

        assertThat(openTypes(shipment)).isEmpty();
    }

    @Test
    void stalledShipmentIsDetectedAgainAfterItsAnomalyWasResolved() {
        Instant now = Instant.now();
        Shipment shipment = save(ShipmentStatus.CREATED, now.minus(Duration.ofDays(5)), null);
        detection.detect();
        scans.record(new ScanEvent(UUID.randomUUID(), shipment.reference(), ScanType.PICKED_UP, "Nantes",
                now.minus(Duration.ofDays(3))));
        assertThat(openTypes(shipment)).isEmpty();

        detection.detect();

        assertThat(openTypes(shipment)).containsExactly(AnomalyType.STALLED);
    }

    /**
     * Le scan est appliqué mais pas encore validé quand la détection démarre : elle attend sa fin (verrou sur
     * l'expédition) au lieu d'ouvrir une STALLED que ce scan ne pourrait plus fermer.
     */
    @Test
    void detectionRunningDuringAScanDoesNotOpenAStalledAnomalyThatScanResolves() throws Exception {
        Shipment shipment = save(ShipmentStatus.CREATED, Instant.now().minus(Duration.ofHours(49)), null);
        CountDownLatch scanApplied = new CountDownLatch(1);
        CountDownLatch commitScan = new CountDownLatch(1);

        CompletableFuture<Void> scanning = CompletableFuture.runAsync(() -> transactions.executeWithoutResult(status -> {
            scan(shipment, ScanType.PICKED_UP);
            scanApplied.countDown();
            await(commitScan);
        }));
        await(scanApplied);
        CompletableFuture<Integer> detecting = CompletableFuture.supplyAsync(detection::detect);
        Thread.sleep(500);
        commitScan.countDown();
        CompletableFuture.allOf(scanning, detecting).get(30, TimeUnit.SECONDS);

        assertThat(openTypes(shipment)).isEmpty();
    }

    @Test
    void detectionIsScheduledEveryFifteenMinutesByDefault() {
        assertThat(scheduledTasks.stream().flatMap(holder -> holder.getScheduledTasks().stream()))
                .map(scheduled -> scheduled.getTask())
                .filteredOn(FixedDelayTask.class::isInstance)
                .map(FixedDelayTask.class::cast)
                .anySatisfy(task -> {
                    assertThat(task.getRunnable().toString()).contains("AnomalyDetectionTask.detect");
                    assertThat(task.getIntervalDuration()).isEqualTo(Duration.ofMinutes(15));
                    assertThat(task.getInitialDelayDuration()).isEqualTo(Duration.ofMinutes(15));
                });
    }

    private int detectAfter(CyclicBarrier start) {
        try {
            start.await();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return detection.detect();
    }

    private static void await(CountDownLatch latch) {
        try {
            assertThat(latch.await(30, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }

    private void scan(Shipment shipment, ScanType type) {
        scans.record(new ScanEvent(UUID.randomUUID(), shipment.reference(), type, "Nantes", Instant.now()));
    }

    private Shipment save(ShipmentStatus status, Instant createdAt, Instant expectedDeliveryAt) {
        return shipments.save(new Shipment(UUID.randomUUID(), "ANO-" + UUID.randomUUID(), "Nantes", "Lyon",
                status, createdAt, expectedDeliveryAt));
    }

    private List<AnomalyType> openTypes(Shipment shipment) {
        return anomalies.findOpen().stream()
                .filter(a -> a.shipmentId().equals(shipment.id()))
                .map(Anomaly::type)
                .toList();
    }
}
