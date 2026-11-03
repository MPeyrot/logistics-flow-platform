package fr.michael.logistics.ingestion.infrastructure.security;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import org.springframework.test.context.DynamicPropertyRegistry;

/** Simule shipment-service : une clé de test, son JWKS servi par WireMock, et des jetons signés. */
public final class TestJwt {

    private static final RSAKey KEY = generate("test-key");
    private static final RSAKey OTHER_KEY = generate("test-key");
    private static final WireMockServer SHIPMENT_SERVICE = start();

    private TestJwt() {
    }

    public static void register(DynamicPropertyRegistry registry) {
        registry.add("shipment-service.url", SHIPMENT_SERVICE::baseUrl);
    }

    /** Jeton SCANNER valide pour le site donné ; à personnaliser avec {@link Builder}. */
    public static Builder scanner(String site) {
        return new Builder().roles("SCANNER").site(site);
    }

    public static final class Builder {
        private String issuer = "shipment-service";
        private String audience = "ingestion-service";
        private List<String> roles = List.of();
        private String site;
        private Instant expiresAt = Instant.now().plusSeconds(3600);
        private RSAKey signingKey = KEY;

        public Builder roles(String... roles) {
            this.roles = List.of(roles);
            return this;
        }

        public Builder site(String site) {
            this.site = site;
            return this;
        }

        public Builder audience(String audience) {
            this.audience = audience;
            return this;
        }

        public Builder expiresAt(Instant expiresAt) {
            this.expiresAt = expiresAt;
            return this;
        }

        public Builder signedWithOtherKey() {
            this.signingKey = OTHER_KEY;
            return this;
        }

        public String build() {
            try {
                JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                        .issuer(issuer).subject("scanner-1").audience(audience)
                        .issueTime(Date.from(Instant.now().minusSeconds(10)))
                        .expirationTime(Date.from(expiresAt))
                        .claim("roles", roles);
                if (site != null) {
                    claims.claim("site", site);
                }
                SignedJWT jwt = new SignedJWT(
                        new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(signingKey.getKeyID())
                                .type(JOSEObjectType.JWT).build(),
                        claims.build());
                jwt.sign(new RSASSASigner(signingKey));
                return jwt.serialize();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
    }

    private static RSAKey generate(String keyId) {
        try {
            return new RSAKeyGenerator(2048).keyID(keyId).generate();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static WireMockServer start() {
        WireMockServer server = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        server.start();
        server.stubFor(get(urlEqualTo("/.well-known/jwks.json")).willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody(new JWKSet(KEY.toPublicJWK()).toString())));
        return server;
    }
}
