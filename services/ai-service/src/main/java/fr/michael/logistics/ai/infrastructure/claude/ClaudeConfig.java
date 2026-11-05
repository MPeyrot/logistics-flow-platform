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

    /**
     * Tarif du modèle configuré. Démarrage refusé si le modèle n'a pas de tarif complet dans
     * {@code ai.claude.pricing.<modèle>.*} : costUsd serait sinon calculé avec le tarif d'un autre modèle.
     */
    @Bean
    ModelPricing modelPricing(ClaudeProperties props) {
        var p = props.pricing() == null ? null : props.pricing().get(props.model());
        if (p == null || p.input() == null || p.output() == null || p.cacheWrite() == null || p.cacheRead() == null) {
            throw new IllegalStateException("No complete pricing for model " + props.model()
                    + ": set ai.claude.pricing." + props.model()
                    + ".{input,output,cache-write,cache-read} in application.properties");
        }
        return new ModelPricing(p.input(), p.output(), p.cacheWrite(), p.cacheRead());
    }
}
