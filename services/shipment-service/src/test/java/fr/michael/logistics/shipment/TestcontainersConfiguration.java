package fr.michael.logistics.shipment;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.lifecycle.Startable;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Un seul PostgreSQL et un seul Kafka pour toute la JVM de test (#61), démarrés au premier usage et partagés par tous
 * les contextes Spring. Leur {@code stop()} ne fait rien : Spring Boot les arrêterait à la fermeture d'un contexte
 * (constaté), Ryuk les retire à la fin de la JVM. Les tests partagent donc base et topics : chacun ne regarde que ses propres données.
 * Importe aussi la clé de signature et l'ADMIN de test.
 */
@TestConfiguration(proxyBeanMethods = false)
@Import(TestSecurityConfiguration.class)
public class TestcontainersConfiguration {

	private static final PostgreSQLContainer POSTGRES =
			new PostgreSQLContainer(DockerImageName.parse("postgres:17")) {
				@Override
				public void stop() {
					// Partagé par tous les contextes : Spring l'arrêterait à la fermeture du premier.
				}
			};

	/** 3.9.1 : la 3.9.0 refuse de démarrer avec la configuration d'écouteurs de Testcontainers. */
	private static final KafkaContainer KAFKA = new KafkaContainer(DockerImageName.parse("apache/kafka:3.9.1")) {
		@Override
		public void stop() {
			// Idem.
		}
	};

	@Bean
	@ServiceConnection
	PostgreSQLContainer postgresContainer() {
		return started(POSTGRES);
	}

	@Bean
	@ServiceConnection
	KafkaContainer kafkaContainer() {
		return started(KAFKA);
	}

	private static synchronized <C extends Startable> C started(C container) {
		container.start(); // sans effet si déjà démarré
		return container;
	}

}
