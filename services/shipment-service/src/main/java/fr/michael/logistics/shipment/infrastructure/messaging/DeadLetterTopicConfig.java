package fr.michael.logistics.shipment.infrastructure.messaging;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
class DeadLetterTopicConfig {

    /**
     * Déclaré au démarrage (KafkaAdmin) : un Kafka managé ne crée pas forcément les topics à la volée.
     * Répliques : {@code kafka.topics.replicas} (1 en local, 3 avec le profil confluent).
     */
    @Bean
    NewTopic scanEventsDeadLetterTopic(@Value("${kafka.topics.replicas:1}") int replicas) {
        return TopicBuilder.name(DeadLetterConfig.DLT_TOPIC).partitions(3).replicas(replicas).build();
    }
}
