package fr.michael.logistics.ingestion.infrastructure.messaging;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
class KafkaTopicsConfig {

    static final String SCAN_EVENTS = "scan-events";

    /** Le producteur possède le topic. Clé = référence d'expédition, donc ordre garanti par expédition. */
    @Bean
    NewTopic scanEventsTopic(@Value("${kafka.topics.replicas:1}") int replicas) {
        return TopicBuilder.name(SCAN_EVENTS).partitions(3).replicas(replicas).build();
    }
}
