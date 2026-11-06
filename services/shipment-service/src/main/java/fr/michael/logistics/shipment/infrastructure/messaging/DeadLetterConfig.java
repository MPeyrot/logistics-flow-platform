package fr.michael.logistics.shipment.infrastructure.messaging;

import fr.michael.logistics.shipment.application.DeadLetterOutbox;
import fr.michael.logistics.shipment.application.DeadLetterOutboxPublisher;
import fr.michael.logistics.shipment.application.DeadLetterReplays;
import fr.michael.logistics.shipment.application.DeadLetterService;
import fr.michael.logistics.shipment.application.ReplayAllJobs;
import fr.michael.logistics.shipment.application.ScanEventDeadLetters;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.Executor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.transaction.support.TransactionOperations;

@Configuration
class DeadLetterConfig {

    static final String DLT_TOPIC = ScanEventListener.TOPIC + ".DLT";

    @Bean
    ScanEventDeadLetters scanEventDeadLetters(ConsumerFactory<?, ?> consumers, KafkaOperations<String, String> kafka,
            @Value("${shipment.kafka.dlt.timeout:10s}") Duration timeout) {
        return new KafkaScanEventDeadLetters(consumers.getConfigurationProperties(), kafka, DLT_TOPIC,
                ScanEventListener.TOPIC, timeout);
    }

    /** « Tout rejouer » tourne sur l'exécuteur de tâches de Spring Boot (spring.task.execution.*). */
    @Bean
    DeadLetterService deadLetterService(ScanEventDeadLetters deadLetters, DeadLetterReplays replays,
            DeadLetterOutbox outbox, ReplayAllJobs jobs, TransactionOperations transactions, Clock clock,
            @Qualifier("applicationTaskExecutor") Executor background) {
        return new DeadLetterService(deadLetters, replays, outbox, jobs, transactions, clock, background);
    }

    @Bean
    DeadLetterOutboxPublisher deadLetterOutboxPublisher(DeadLetterOutbox outbox, ScanEventDeadLetters deadLetters,
            TransactionOperations transactions, Clock clock,
            @Value("${shipment.kafka.dlt.outbox.batch-size:100}") int batchSize) {
        return new DeadLetterOutboxPublisher(outbox, deadLetters, transactions, clock, batchSize);
    }
}
