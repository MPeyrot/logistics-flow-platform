package fr.michael.logistics.shipment;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** Conteneurs PostgreSQL et Kafka ; importe aussi la clé de signature et l'ADMIN de test. */
@TestConfiguration(proxyBeanMethods = false)
@Import(TestSecurityConfiguration.class)
public class TestcontainersConfiguration {

	@Bean
	@ServiceConnection
	PostgreSQLContainer postgresContainer() {
		return new PostgreSQLContainer(DockerImageName.parse("postgres:17"));
	}

	/** 3.8.0 : la 3.9.0 refuse de démarrer avec la configuration d'écouteurs de Testcontainers. */
	@Bean
	@ServiceConnection
	KafkaContainer kafkaContainer() {
		return new KafkaContainer(DockerImageName.parse("apache/kafka:3.8.0"));
	}

}
