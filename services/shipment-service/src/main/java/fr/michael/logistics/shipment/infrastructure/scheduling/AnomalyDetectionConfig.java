package fr.michael.logistics.shipment.infrastructure.scheduling;

import fr.michael.logistics.shipment.application.AnomalyRepository;
import fr.michael.logistics.shipment.application.DetectAnomaliesService;
import fr.michael.logistics.shipment.application.ScanRepository;
import fr.michael.logistics.shipment.application.ShipmentRepository;
import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.support.TransactionOperations;

/** Détection des anomalies (application.properties, shipment.anomaly.*). */
@Configuration
@EnableScheduling
class AnomalyDetectionConfig {

    @Bean
    DetectAnomaliesService detectAnomaliesService(
            ShipmentRepository shipments,
            ScanRepository scans,
            AnomalyRepository anomalies,
            TransactionOperations transactions,
            Clock clock,
            @Value("${shipment.anomaly.stalled-after}") Duration stalledAfter) {
        return new DetectAnomaliesService(shipments, scans, anomalies, transactions, clock, stalledAfter);
    }
}
