package fr.michael.logistics.shipment.infrastructure.messaging;

import fr.michael.logistics.shipment.application.ShipmentNotFoundException;
import fr.michael.logistics.shipment.domain.InvalidScanException;
import java.time.Duration;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.ConsumerAwareRecordRecoverer;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.ListenerExecutionFailedException;
import org.springframework.util.backoff.BackOff;
import org.springframework.util.backoff.ExponentialBackOff;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
class KafkaConsumerConfig {

    /** Défaut du client Kafka (5 min), si spring.kafka.consumer.properties.max.poll.interval.ms n'est pas fixé. */
    private static final long DEFAULT_MAX_POLL_INTERVAL_MS = 300_000;

    /**
     * Expédition pas encore créée : backoff exponentiel bloquant ({@code shipment.kafka.unknown-shipment.*}),
     * la partition attend pour garder l'ordre par expédition. Autre erreur transitoire (base indisponible) :
     * nouvelles tentatives à intervalle fixe. Dans les deux cas, puis topic {@code scan-events.DLT}.
     * Message invalide ou transition interdite : DLT immédiat.
     */
    @Bean
    DefaultErrorHandler errorHandler(
            KafkaOperations<String, String> kafka,
            @Value("${shipment.kafka.retry-interval-ms:1000}") long intervalMs,
            @Value("${shipment.kafka.retry-attempts:5}") long attempts,
            @Value("${shipment.kafka.unknown-shipment.initial-interval}") Duration initialInterval,
            @Value("${shipment.kafka.unknown-shipment.multiplier}") double multiplier,
            @Value("${shipment.kafka.unknown-shipment.max-interval}") Duration maxInterval,
            @Value("${shipment.kafka.unknown-shipment.max-elapsed-time}") Duration maxElapsedTime,
            @Value("${spring.kafka.consumer.properties.max.poll.interval.ms:" + DEFAULT_MAX_POLL_INTERVAL_MS + "}")
            long maxPollIntervalMs) {
        // Chaque attente dort dans le thread du consommateur, sans poll() : au-delà de max.poll.interval.ms,
        // le consommateur quitterait le groupe (rééquilibrage, partition relue par un autre).
        if (maxInterval.toMillis() >= maxPollIntervalMs) {
            throw new IllegalStateException(("shipment.kafka.unknown-shipment.max-interval (%d ms) must stay under "
                    + "max.poll.interval.ms (%d ms)").formatted(maxInterval.toMillis(), maxPollIntervalMs));
        }
        // partition -1 : le producteur choisit (le DLT n'a pas forcément autant de partitions que le topic source)
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafka, (record, e) -> new TopicPartition(record.topic() + ".DLT", -1));
        // En-têtes kafka_dlt-exception-* : l'erreur de l'écouteur, pas son enveloppe ListenerExecutionFailedException
        // (lus par l'outil ADMIN du DLT).
        ConsumerAwareRecordRecoverer unwrapping = (record, consumer, e) ->
                recoverer.accept(record, consumer, unwrapListenerFailure(e));
        DefaultErrorHandler handler = new DefaultErrorHandler(unwrapping, new FixedBackOff(intervalMs, attempts));
        handler.addNotRetryableExceptions(InvalidMessageException.class, InvalidScanException.class);

        ExponentialBackOff unknownShipment = new ExponentialBackOff(initialInterval.toMillis(), multiplier);
        unknownShipment.setMaxInterval(maxInterval.toMillis());
        unknownShipment.setMaxElapsedTime(maxElapsedTime.toMillis());
        // null : le backoff par défaut (FixedBackOff) s'applique
        handler.setBackOffFunction((record, e) -> causedByUnknownShipment(e) ? unknownShipment : null);
        return handler;
    }

    private static Exception unwrapListenerFailure(Exception e) {
        return e instanceof ListenerExecutionFailedException && e.getCause() instanceof Exception cause ? cause : e;
    }

    /** L'écouteur enveloppe l'exception (ListenerExecutionFailedException) : on parcourt les causes. */
    private static boolean causedByUnknownShipment(Throwable e) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof ShipmentNotFoundException) {
                return true;
            }
        }
        return false;
    }
}
