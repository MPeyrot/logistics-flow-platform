package fr.michael.logistics.shipment.infrastructure.ai;

import fr.michael.logistics.shipment.application.ExtractionRateLimiter;
import fr.michael.logistics.shipment.application.ServiceTokenProvider;
import fr.michael.logistics.shipment.application.TokenIssuer;
import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AiServiceProperties.class)
class AiServiceConfig {

    private static final String AI_SERVICE_AUDIENCE = "ai-service";

    @Bean
    ServiceTokenProvider aiServiceTokenProvider(TokenIssuer issuer, Clock clock) {
        return new ServiceTokenProvider(AI_SERVICE_AUDIENCE, issuer, clock);
    }

    /** 20 extractions par heure et par utilisateur par défaut (extraction.rate-limit.*). */
    @Bean
    ExtractionRateLimiter extractionRateLimiter(
            @Value("${extraction.rate-limit.max-per-window}") int maxPerWindow,
            @Value("${extraction.rate-limit.window}") Duration window,
            Clock clock) {
        return new ExtractionRateLimiter(maxPerWindow, window, clock);
    }
}
