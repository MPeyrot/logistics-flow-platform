package fr.michael.logistics.shipment.infrastructure.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.RSAKey;
import fr.michael.logistics.shipment.application.AuthenticationService;
import jakarta.servlet.DispatcherType;
import java.util.List;
import java.util.Set;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Matrice d'accès de la spec #13. shipment-service valide lui-même les jetons qu'il émet, avec sa clé publique.
 */
@Configuration
class SecurityConfig {

    /**
     * Publics des jetons utilisateurs. ingestion-service est accepté pour qu'un SCANNER, authentifié par
     * un jeton valide, reçoive 403 (rôle insuffisant) et non 401. Le jeton de service (aud=ai-service) est refusé.
     */
    private static final Set<String> ACCEPTED_AUDIENCES = Set.of(
            AuthenticationService.SHIPMENT_AUDIENCE, AuthenticationService.INGESTION_AUDIENCE);

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtDecoder jwtDecoder,
            ProblemDetailSecurityHandler problems) throws Exception {
        http
                .csrf(csrf -> csrf.disable()) // API sans session ni cookie : jeton Bearer seulement
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/token").permitAll()
                        .requestMatchers(HttpMethod.GET, "/.well-known/jwks.json").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/shipments").hasAnyRole("ADMIN", "OPERATOR")
                        .requestMatchers(HttpMethod.GET, "/api/anomalies").hasAnyRole("ADMIN", "OPERATOR")
                        .requestMatchers(HttpMethod.POST, "/api/shipments").hasAnyRole("ADMIN", "OPERATOR")
                        .requestMatchers(HttpMethod.POST, "/api/delivery-notes/extract").hasAnyRole("ADMIN", "OPERATOR")
                        .requestMatchers(HttpMethod.POST, "/api/users").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/users/*").hasRole("ADMIN")
                        .anyRequest().denyAll())
                .oauth2ResourceServer(resource -> resource
                        .jwt(jwt -> jwt.decoder(jwtDecoder).jwtAuthenticationConverter(rolesAsAuthorities()))
                        .authenticationEntryPoint(problems)
                        .accessDeniedHandler(problems))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint(problems)
                        .accessDeniedHandler(problems));
        return http.build();
    }

    @Bean
    JwtDecoder jwtDecoder(RSAKey signingKey) throws JOSEException {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(signingKey.toRSAPublicKey())
                .signatureAlgorithm(SignatureAlgorithm.RS256)
                .build();
        JwtClaimValidator<List<String>> audience = new JwtClaimValidator<>(JwtClaimNames.AUD,
                aud -> aud != null && aud.stream().anyMatch(ACCEPTED_AUDIENCES::contains));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(AuthenticationService.ISSUER), audience));
        return decoder;
    }

    /** Claim roles = ["ADMIN"] devient l'autorité ROLE_ADMIN. */
    private static JwtAuthenticationConverter rolesAsAuthorities() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("roles");
        authorities.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }
}
