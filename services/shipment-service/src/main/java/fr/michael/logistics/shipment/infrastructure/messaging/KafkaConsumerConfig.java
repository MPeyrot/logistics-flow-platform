package fr.michael.logistics.shipment.infrastructure.messaging;

import fr.michael.logistics.shipment.domain.InvalidScanException;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
class KafkaConsumerConfig {

    /**
     * Erreur transitoire (expédition pas encore créée, base indisponible) : nouvelles tentatives,
     * puis topic {@code scan-events.DLT}. Message invalide ou transition interdite : DLT immédiat.
     */
    @Bean
    DefaultErrorHandler errorHandler(
            KafkaOperations<String, String> kafka,
            @Value("${shipment.kafka.retry-interval-ms:1000}") long intervalMs,
            @Value("${shipment.kafka.retry-attempts:5}") long attempts) {
        // partition -1 : le producteur choisit (le DLT n'a pas forcément autant de partitions que le topic source)
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafka, (record, e) -> new TopicPartition(record.topic() + ".DLT", -1));
        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, new FixedBackOff(intervalMs, attempts));
        handler.addNotRetryableExceptions(InvalidMessageException.class, InvalidScanException.class);
        return handler;
    }
}
