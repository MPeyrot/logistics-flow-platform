package fr.michael.logistics.shipment.infrastructure.messaging;

import fr.michael.logistics.shipment.application.ScanEventDeadLetters;
import java.time.Duration;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaOperations;

@Configuration
class DeadLetterConfig {

    static final String DLT_TOPIC = ScanEventListener.TOPIC + ".DLT";

    /**
     * Déclaré au démarrage (KafkaAdmin) : un Kafka managé ne crée pas forcément les topics à la volée.
     * Facteur de réplication : celui du broker.
     */
    @Bean
    NewTopic scanEventsDeadLetterTopic() {
        return TopicBuilder.name(DLT_TOPIC).partitions(3).build();
    }

    @Bean
    ScanEventDeadLetters scanEventDeadLetters(ConsumerFactory<?, ?> consumers, KafkaOperations<String, String> kafka,
            @Value("${shipment.kafka.dlt.timeout:10s}") Duration timeout) {
        return new KafkaScanEventDeadLetters(consumers.getConfigurationProperties(), kafka, DLT_TOPIC,
                ScanEventListener.TOPIC, timeout);
    }
}
