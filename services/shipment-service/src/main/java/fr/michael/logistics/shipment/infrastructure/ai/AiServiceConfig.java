package fr.michael.logistics.shipment.infrastructure.ai;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AiServiceProperties.class)
class AiServiceConfig {
}
