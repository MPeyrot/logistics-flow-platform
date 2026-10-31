package fr.michael.logistics.ai.infrastructure.claude;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import fr.michael.logistics.ai.domain.ModelPricing;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ClaudeProperties.class)
class ClaudeConfig {

    /** Valeur factice quand la clé manque : l'extracteur refuse alors d'appeler l'API (503). */
    private static final String PLACEHOLDER_KEY = "missing-api-key";

    @Bean
    AnthropicClient anthropicClient(ClaudeProperties props) {
        var builder = AnthropicOkHttpClient.builder()
                .apiKey(props.hasApiKey() ? props.apiKey() : PLACEHOLDER_KEY)
                .maxRetries(props.maxRetries())
                .timeout(props.timeout());
        if (props.baseUrl() != null && !props.baseUrl().isBlank()) {
            builder.baseUrl(props.baseUrl());
        }
        return builder.build();
    }

    @Bean
    ModelPricing modelPricing(ClaudeProperties props) {
        var p = props.pricing();
        return new ModelPricing(p.input(), p.output(), p.cacheWrite(), p.cacheRead());
    }
}
