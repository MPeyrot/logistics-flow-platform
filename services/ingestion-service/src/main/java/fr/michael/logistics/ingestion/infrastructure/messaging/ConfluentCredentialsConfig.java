package fr.michael.logistics.ingestion.infrastructure.messaging;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.util.StringUtils;

/** Avec le profil {@code confluent}, refuse de démarrer sans clé d'API plutôt que d'échouer à l'authentification. */
@Configuration
@Profile("confluent")
class ConfluentCredentialsConfig {

    ConfluentCredentialsConfig(@Value("${KAFKA_API_KEY:}") String apiKey,
            @Value("${KAFKA_API_SECRET:}") String apiSecret) {
        if (!StringUtils.hasText(apiKey) || !StringUtils.hasText(apiSecret)) {
            throw new IllegalStateException(
                    "Profil confluent : les variables d'environnement KAFKA_API_KEY et KAFKA_API_SECRET sont obligatoires.");
        }
    }
}
