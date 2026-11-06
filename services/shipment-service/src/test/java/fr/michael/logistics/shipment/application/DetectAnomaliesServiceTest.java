package fr.michael.logistics.shipment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import fr.michael.logistics.shipment.domain.Anomaly;
import fr.michael.logistics.shipment.domain.AnomalyType;
import fr.michael.logistics.shipment.domain.Shipment;
import fr.michael.logistics.shipment.domain.ShipmentStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionOperations;

/** La passe de détection survit à l'échec d'une expédition (verrou, deadlock, coupure de base). */
class DetectAnomaliesServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-06T10:00:00Z");
    private static final Duration STALLED_AFTER = Duration.ofHours(48);

    private final ShipmentRepository shipments = mock(ShipmentRepository.class);
    private final ScanRepository scans = mock(ScanRepository.class);
    private final AnomalyRepository anomalies = mock(AnomalyRepository.class);
    private final TransactionOperations transactions = new TransactionOperations() {
        @Override
        public <T> T execute(org.springframework.transaction.support.TransactionCallback<T> action) {
            return action.doInTransaction(null);
        }
    };
    private final DetectAnomaliesService service = new DetectAnomaliesService(
            shipments, scans, anomalies, transactions, Clock.fixed(NOW, ZoneOffset.UTC), STALLED_AFTER);

    @Test
    void failureOnOneShipmentDoesNotStopTheOthers() {
        Shipment broken = lateShipment();
        Shipment healthy = lateShipment();
        when(shipments.findAnomalyCandidateIds(NOW, NOW.minus(STALLED_AFTER)))
                .thenReturn(List.of(broken.id(), healthy.id()));
        when(shipments.lockById(broken.id())).thenThrow(new IllegalStateException("deadlock"));
        when(shipments.lockById(healthy.id())).thenReturn(Optional.of(healthy));
        when(scans.lastReceivedAt(healthy.id())).thenReturn(Optional.of(NOW.minusSeconds(60)));
        when(anomalies.openIfAbsent(any())).thenReturn(true);

        int opened = service.detect();

        assertThat(opened).isEqualTo(1);
        verify(anomalies).openIfAbsent(org.mockito.ArgumentMatchers.argThat(
                (Anomaly a) -> a.shipmentId().equals(healthy.id()) && a.type() == AnomalyType.LATE));
    }

    private static Shipment lateShipment() {
        return new Shipment(UUID.randomUUID(), "REF-" + UUID.randomUUID(), "Nantes", "Lyon",
                ShipmentStatus.IN_TRANSIT, NOW.minus(Duration.ofDays(1)), NOW.minusSeconds(3_600));
    }
}
