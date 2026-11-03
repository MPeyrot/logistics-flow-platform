package fr.michael.logistics.ai.infrastructure.security;

import jakarta.servlet.DispatcherType;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import tools.jackson.databind.json.JsonMapper;

/**
 * Resource server : seul shipment-service appelle ai-service, avec un jeton de service qu'il émet.
 * La clé publique (JWKS) est lue au premier jeton présenté, pas au démarrage.
 */
@Configuration
class SecurityConfig {

    static final String ISSUER = "shipment-service";
    static final String AUDIENCE = "ai-service";
    static final String SUBJECT = "shipment-service";

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JsonMapper json) {
        ProblemDetailSecurityHandler problems = new ProblemDetailSecurityHandler(json);
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        // forward vers /error (400, 413, 500…) : sans cela, il tombe sur denyAll et devient 403
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/delivery-notes/extract").authenticated()
                        .anyRequest().denyAll())
                .oauth2ResourceServer(o -> o
                        .jwt(j -> { })
                        .authenticationEntryPoint(problems)
                        .accessDeniedHandler(problems))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(problems)
                        .accessDeniedHandler(problems));
        return http.build();
    }

    @Bean
    JwtDecoder jwtDecoder(@Value("${shipment-service.url}") String shipmentServiceUrl) {
        String base = shipmentServiceUrl.endsWith("/")
                ? shipmentServiceUrl.substring(0, shipmentServiceUrl.length() - 1) : shipmentServiceUrl;
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(base + "/.well-known/jwks.json").build();
        OAuth2TokenValidator<Jwt> audience = jwt -> jwt.getAudience() != null && jwt.getAudience().contains(AUDIENCE)
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Wrong audience", null));
        OAuth2TokenValidator<Jwt> subject = jwt -> SUBJECT.equals(jwt.getSubject())
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Wrong subject", null));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(List.of(
                JwtValidators.createDefaultWithIssuer(ISSUER), audience, subject)));
        return decoder;
    }
}
