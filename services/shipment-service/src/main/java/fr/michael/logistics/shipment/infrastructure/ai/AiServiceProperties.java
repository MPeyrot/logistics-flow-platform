package fr.michael.logistics.shipment.infrastructure.ai;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("ai-service")
public record AiServiceProperties(String baseUrl, Duration connectTimeout, Duration readTimeout) {
}
